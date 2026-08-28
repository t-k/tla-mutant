package dev.tlamutant.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import tla2sany.semantic.SemanticNode;
import tlc2.tool.Action;
import tlc2.tool.TLCState;
import tlc2.util.BitVector;
import tlc2.util.IStateWriter;
import util.UniqueString;

public final class CanonicalStateWriter implements IStateWriter {
    private static final String PATH_PROPERTY = "tla.mutant.state.path";
    private static final String MAX_STATES_PROPERTY = "tla.mutant.state.maxStates";
    private static final String MAX_BYTES_PROPERTY = "tla.mutant.state.maxBytes";

    private final ObjectMapper mapper = new ObjectMapper();
    private final TlcValueCanonicalizer canonicalizer = new TlcValueCanonicalizer();
    private final Path outputPath;
    private final OutputStream output;
    private final long maximumStates;
    private final long maximumBytes;
    private final Set<String> identities = new HashSet<>();
    private long bytesWritten;
    private boolean closed;

    public CanonicalStateWriter() throws IOException {
        outputPath = validatedOutputPath(System.getProperty(PATH_PROPERTY));
        maximumStates = positiveLongProperty(MAX_STATES_PROPERTY);
        maximumBytes = positiveLongProperty(MAX_BYTES_PROPERTY);
        output = Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Override
    public synchronized void writeState(TLCState state) {
        emit(state);
    }

    @Override
    public synchronized void writeState(TLCState state, TLCState successor, short stateFlags) {
        emitUnseen(successor, stateFlags);
    }

    @Override
    public synchronized void writeState(TLCState state, TLCState successor, short stateFlags, Action action) {
        emitUnseen(successor, stateFlags);
    }

    @Override
    public synchronized void writeState(
            TLCState state, TLCState successor, short stateFlags, Action action, SemanticNode predicate) {
        emitUnseen(successor, stateFlags);
    }

    @Override
    public synchronized void writeState(
            TLCState state, TLCState successor, short stateFlags, Visualization visualization) {
        emitUnseen(successor, stateFlags);
    }

    @Override
    public synchronized void writeState(
            TLCState state, TLCState successor, BitVector actionChecks, int from, int to, short stateFlags) {
        emitUnseen(successor, stateFlags);
    }

    @Override
    public synchronized void writeState(
            TLCState state,
            TLCState successor,
            BitVector actionChecks,
            int from,
            int to,
            short stateFlags,
            Visualization visualization) {
        emitUnseen(successor, stateFlags);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            writeLine(Map.of("type", "complete", "states", identities.size()));
            output.flush();
            output.close();
            closed = true;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to close canonical state output", exception);
        }
    }

    @Override
    public String getDumpFileName() {
        return outputPath.toString();
    }

    @Override
    public boolean isNoop() {
        return false;
    }

    @Override
    public boolean isDot() {
        return false;
    }

    @Override
    public boolean isConstrained() {
        return true;
    }

    @Override
    public synchronized void snapshot() throws IOException {
        output.flush();
    }

    private void emitUnseen(TLCState successor, short stateFlags) {
        if (successor != null && isSet(stateFlags, IsUnseen) && !isSet(stateFlags, IsNotInModel)) {
            emit(successor);
        }
    }

    private void emit(TLCState input) {
        Objects.requireNonNull(input, "state");
        if (!input.allAssigned()) {
            throw new IllegalArgumentException("TLC state is incomplete");
        }
        TLCState state = input.deepCopy();
        state.deepNormalize();
        TreeMap<String, CanonicalValue> variables = new TreeMap<>();
        for (Map.Entry<UniqueString, tlc2.value.IValue> entry : state.getVals().entrySet()) {
            variables.put(entry.getKey().toString(), canonicalizer.canonicalize(entry.getValue()));
        }
        StringBuilder identity = new StringBuilder();
        variables.forEach((name, value) -> identity.append(name.length())
                .append(':')
                .append(name)
                .append(':')
                .append(value.identityText())
                .append(';'));
        String identityText = identity.toString();
        if (!identities.add(identityText)) {
            return;
        }
        if (identities.size() > maximumStates) {
            throw new IllegalStateException("Canonical state count limit exceeded");
        }
        CanonicalState canonicalState = CanonicalState.of(identityText);
        writeLine(new StateLine("state", canonicalState.sha256(), variables));
    }

    private void writeLine(Object value) {
        try {
            byte[] json = mapper.writeValueAsBytes(value);
            long nextSize = bytesWritten + json.length + 1L;
            if (nextSize > maximumBytes) {
                throw new IllegalStateException("Canonical state output byte limit exceeded");
            }
            output.write(json);
            output.write('\n');
            bytesWritten = nextSize;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write canonical state output", exception);
        }
    }

    private static Path validatedOutputPath(String configured) throws IOException {
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("Missing " + PATH_PROPERTY);
        }
        Path path = Path.of(configured);
        if (!path.isAbsolute() || path.getFileName() == null || Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Canonical state path must be a new absolute file");
        }
        Path parent = path.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent)) {
            throw new IllegalArgumentException("Canonical state parent must be a real directory");
        }
        Path realParent = parent.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realParent.equals(parent.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Canonical state parent contains a link or alias");
        }
        return path.normalize();
    }

    private static long positiveLongProperty(String name) {
        String value = System.getProperty(name);
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a positive integer", exception);
        }
    }

    private record StateLine(String type, String sha256, Map<String, CanonicalValue> variables) {}
}
