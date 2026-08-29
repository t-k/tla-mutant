package dev.tlamutant.semantic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tla2sany.drivers.FrontEndException;
import tla2sany.drivers.SANY;
import tla2sany.drivers.SanyExitCode;
import tla2sany.drivers.SanySettings;
import tla2sany.modanalyzer.SpecObj;
import tla2sany.output.SilentSanyOutput;
import tla2sany.semantic.LetInNode;
import tla2sany.semantic.ModuleNode;
import tla2sany.semantic.OpApplNode;
import tla2sany.semantic.OpDefNode;
import tla2sany.semantic.SemanticNode;
import tla2sany.semantic.SubstInNode;
import tlc2.tool.BuiltInOPs;
import tlc2.tool.ToolGlobals;
import tlc2.tool.impl.ModelConfig;
import tlc2.util.Vect;
import util.FilenameToStream;
import util.SimpleFilenameToStream;

public final class SanyAnalyzer {
    private static final Set<String> DEFAULT_STANDARD_MODULES = Set.of(
            "Naturals",
            "Integers",
            "Reals",
            "Sequences",
            "FiniteSets",
            "TLC",
            "TLCExt",
            "JSON",
            "CSV",
            "IOUtils",
            "Randomization",
            "Bags",
            "Relations",
            "Functions",
            "Combinatorics",
            "VectorClocks");

    public SemanticProject analyze(Path inputRoot, String rootModuleFile, String configFile) {
        Objects.requireNonNull(inputRoot, "inputRoot");
        RestrictedResolver resolver = new RestrictedResolver(inputRoot, DEFAULT_STANDARD_MODULES);
        String moduleName = stripSuffix(rootModuleFile, ".tla");
        SpecObj specification = new SpecObj(moduleName, resolver);
        try {
            var result =
                    SANY.parse(specification, rootModuleFile, new SilentSanyOutput(), SanySettings.validAstSettings());
            if (result != SanyExitCode.OK
                    || specification.getParseErrors().isFailure()
                    || specification.getSemanticErrors().isFailure()) {
                throw new IllegalArgumentException("SANY rejected the specification: parse="
                        + specification.getParseErrors()
                        + "; semantic="
                        + specification.getSemanticErrors());
            }
        } catch (FrontEndException exception) {
            throw new IllegalArgumentException("SANY failed to parse the specification", exception);
        }

        ModelConfig config = new ModelConfig(configFile, resolver);
        config.parse();
        ModuleNode module = specification.getRootModule();
        List<RoleGraph.Root> roots = configuredRoots(module, config);
        Map<String, OperatorDefinition> definitions = RoleGraph.classify(moduleName, module.getOpDefs(), roots);
        Set<String> transitions = configuredTransitionDefinitions(module, config, definitions);
        return new SemanticProject(moduleName, definitions, transitions, specification);
    }

    private static Set<String> configuredTransitionDefinitions(
            ModuleNode module, ModelConfig config, Map<String, OperatorDefinition> definitions) {
        Set<OpDefNode> roots = new HashSet<>();
        if (nonBlank(config.getNext())) {
            roots.add(requireDefinition(module, "NEXT", config.getNext()));
        } else {
            OpDefNode specification = requireDefinition(module, "SPECIFICATION", config.getSpec());
            List<SemanticNode> actions = new ArrayList<>();
            collectSquareActions(specification.getBody(), new HashSet<>(), actions);
            if (actions.size() != 1) {
                throw new IllegalArgumentException(
                        "Configured SPECIFICATION must contain exactly one [] [Next]_vars action");
            }
            collectReferencedDefinitions(actions.getFirst(), new HashSet<>(), roots);
        }

        Set<String> result = new HashSet<>();
        ArrayList<OpDefNode> pending = new ArrayList<>(roots);
        while (!pending.isEmpty()) {
            OpDefNode definition = pending.removeLast();
            String name = definition.getName().toString();
            OperatorDefinition known = definitions.get(name);
            if (known == null || !result.add(name)) {
                continue;
            }
            Set<OpDefNode> references = new HashSet<>();
            collectReferencedDefinitions(definition.getBody(), new HashSet<>(), references);
            pending.addAll(references);
        }
        return Set.copyOf(result);
    }

    private static void collectSquareActions(SemanticNode node, Set<Integer> visited, List<SemanticNode> result) {
        if (node == null || !visited.add(node.getUid())) {
            return;
        }
        if (node instanceof LetInNode letIn) {
            collectSquareActions(letIn.getBody(), visited, result);
            return;
        }
        if (node instanceof SubstInNode substitution) {
            collectSquareActions(substitution.getBody(), visited, result);
            return;
        }
        if (!(node instanceof OpApplNode application)) {
            return;
        }
        int opcode = BuiltInOPs.getOpCode(application.getOperator().getName());
        if (opcode == ToolGlobals.OPCODE_wf || opcode == ToolGlobals.OPCODE_sf) {
            return;
        }
        if (application.getOperator() instanceof OpDefNode definition && definition.getArity() == 0) {
            collectSquareActions(definition.getBody(), visited, result);
            return;
        }
        if (opcode == ToolGlobals.OPCODE_box
                && application.getArgs().length == 1
                && application.getArgs()[0] instanceof OpApplNode squareAction
                && squareAction.hasOpcode(ToolGlobals.OPCODE_sa)
                && squareAction.getArgs().length == 2) {
            result.add(squareAction.getArgs()[0]);
            return;
        }
        for (SemanticNode child : application.getChildren()) {
            collectSquareActions(child, visited, result);
        }
    }

    private static void collectReferencedDefinitions(
            SemanticNode node, Set<Integer> visited, Set<OpDefNode> definitions) {
        if (node == null || !visited.add(node.getUid())) {
            return;
        }
        if (node instanceof OpApplNode application && application.getOperator() instanceof OpDefNode definition) {
            definitions.add(definition);
        }
        SemanticNode[] children = node.getChildren();
        if (children != null) {
            for (SemanticNode child : children) {
                collectReferencedDefinitions(child, visited, definitions);
            }
        }
    }

    private static OpDefNode requireDefinition(ModuleNode module, String label, String name) {
        OpDefNode definition = module.getOpDef(name);
        if (definition == null) {
            throw new IllegalArgumentException("Configured root cannot be resolved: " + label + " " + name);
        }
        return definition;
    }

    private static List<RoleGraph.Root> configuredRoots(ModuleNode module, ModelConfig config) {
        List<RoleGraph.Root> roots = new ArrayList<>();
        if (nonBlank(config.getSpec())) {
            roots.add(root(module, SemanticRole.MODEL, "SPECIFICATION", config.getSpec()));
        } else {
            if (nonBlank(config.getInit())) {
                roots.add(root(module, SemanticRole.MODEL, "INIT", config.getInit()));
            }
            if (nonBlank(config.getNext())) {
                roots.add(root(module, SemanticRole.MODEL, "NEXT", config.getNext()));
            }
        }
        addVectRoots(roots, module, SemanticRole.PROPERTY, "INVARIANT", config.getInvariants());
        addVectRoots(roots, module, SemanticRole.PROPERTY, "PROPERTY", config.getProperties());
        addVectRoots(roots, module, SemanticRole.SCOPE, "CONSTRAINT", config.getConstraints());
        addVectRoots(roots, module, SemanticRole.SCOPE, "ACTION_CONSTRAINT", config.getActionConstraints());
        if (nonBlank(config.getView())) {
            roots.add(root(module, SemanticRole.SCOPE, "VIEW", config.getView()));
        }
        if (nonBlank(config.getSymmetry())) {
            roots.add(root(module, SemanticRole.SCOPE, "SYMMETRY", config.getSymmetry()));
        }
        if (roots.stream().noneMatch(candidate -> candidate.role() == SemanticRole.MODEL)) {
            throw new IllegalArgumentException("Configuration has no model root");
        }
        return List.copyOf(roots);
    }

    private static void addVectRoots(
            List<RoleGraph.Root> roots, ModuleNode module, SemanticRole role, String label, Vect<?> values) {
        for (int index = 0; index < values.size(); index++) {
            roots.add(root(module, role, label, values.elementAt(index).toString()));
        }
    }

    private static RoleGraph.Root root(ModuleNode module, SemanticRole role, String label, String name) {
        OpDefNode definition = requireDefinition(module, label, name);
        return new RoleGraph.Root(role, label + " " + name, definition);
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String stripSuffix(String value, String suffix) {
        if (value == null || !value.endsWith(suffix) || value.length() == suffix.length()) {
            throw new IllegalArgumentException("Expected file suffix " + suffix);
        }
        return value.substring(0, value.length() - suffix.length());
    }

    private static final class RestrictedResolver implements FilenameToStream {
        private final Path root;
        private final Set<String> standardModules;
        private final SimpleFilenameToStream standardResolver = new SimpleFilenameToStream();

        private RestrictedResolver(Path root, Set<String> standardModules) {
            this.root = root.toAbsolutePath().normalize();
            this.standardModules = Set.copyOf(standardModules);
        }

        @Override
        public File resolve(String filename, boolean isModule) {
            String moduleName =
                    filename.endsWith(".tla") ? filename.substring(0, filename.length() - ".tla".length()) : filename;
            if (isModule && standardModules.contains(moduleName) && standardResolver.isStandardModule(filename)) {
                return standardResolver.resolve(filename, true);
            }
            Path resolved = root.resolve(filename).normalize();
            if (!resolved.startsWith(root) || !Files.isRegularFile(resolved) || Files.isSymbolicLink(resolved)) {
                return new File(root.resolve("__rejected__").toString());
            }
            return new FilenameToStream.TLAFile(resolved, false, this);
        }

        @Override
        public String getFullPath() {
            return root.toString();
        }

        @Override
        public boolean isStandardModule(String moduleName) {
            String stripped = moduleName.endsWith(".tla")
                    ? moduleName.substring(0, moduleName.length() - ".tla".length())
                    : moduleName;
            return standardModules.contains(stripped) && standardResolver.isStandardModule(moduleName);
        }
    }
}
