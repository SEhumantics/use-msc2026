package org.tzi.use.uncertainty.differential;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.SBooleanValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UBooleanValue;
import org.tzi.use.uml.ocl.value.UIntegerValue;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.ocl.value.UStringValue;
import org.tzi.use.uml.ocl.value.Value;

/**
 * The <em>ported</em> side of the differential comparison: the classes this port actually wrote,
 * loaded from the reactor by the ordinary application class loader.
 *
 * <p>Until S4 this side was {@link StubCandidate}, because there was nothing to compare against.
 * Every fidelity figure the project quotes from S4 onward comes through here.
 *
 * <h2>Three deliberate choices, each of which a defect report is behind</h2>
 *
 * <ol>
 *   <li><b>Values are constructed directly, operations are dispatched reflectively.</b> Construction
 *       is compile-time typed because these classes are on the classpath and there is no reason to
 *       give that up. Dispatch is by name because the <em>operation set</em> is what is under test:
 *       resolving {@code add} reflectively is what makes a missing or renamed method show up as
 *       {@link DiffVerdict#UNSUPPORTED} rather than as a compile error in the harness.</li>
 *
 *   <li><b>The result's class is OBSERVED, never assumed</b> ({@link UValue#observedFrom(Object)},
 *       defect D-43). This side can finally do that, because the object it reports on is one it
 *       actually produced. At S1 the ported token had to be assumed, which is why a type-only
 *       difference was demoted to a counted-not-scored quantity; from S4 the token on BOTH sides is
 *       real, which is the precondition {@code harness-contract.md} §7 sets for turning it back into
 *       a gate clause (D-52).</li>
 *
 *   <li><b>Anything this adapter cannot do raises {@link HarnessMarshallingException}</b>, which is
 *       scored {@link DiffVerdict#HARNESS_ERROR} and is <em>not</em> an agreement (defect D1). A
 *       marshalling failure is the instrument failing, not the port answering.</li>
 * </ol>
 *
 * <p>Opaque rendering is shared with {@link HistoricalOracle#opaqueRepresentation(Object)} on
 * purpose: rendering is the instrument, not the port's behaviour, so it must not itself be a source
 * of difference. The cost is stated there.
 */
public final class PortedCandidate implements Candidate {

    private static final String VALUE_PKG = "org.tzi.use.uml.ocl.value.";

    private final Map<String, Method> methods = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public static PortedCandidate open() {
        return new PortedCandidate();
    }

    private PortedCandidate() {
    }

    @Override
    public String name() {
        return "ported";
    }

    @Override
    public void close() {
        closed = true;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("PortedCandidate is closed");
        }
    }

    // ------------------------------------------------------------------ resolution

    private static Class<?> portedClass(String simpleName) {
        try {
            return Class.forName(VALUE_PKG + simpleName);
        } catch (ClassNotFoundException e) {
            throw new NoSuchPortedClassException(VALUE_PKG + simpleName + " is not on the reactor "
                    + "classpath; the port has not written it", e);
        }
    }

    /** Thrown when the ported class itself is absent — a different fact from a missing method. */
    static final class NoSuchPortedClassException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NoSuchPortedClassException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private Method resolve(UOp op) {
        Method cached = methods.get(op.key());
        if (cached != null) {
            return cached;
        }
        Class<?> receiver = portedClass(op.receiverType());
        Method found = null;
        for (Method m : receiver.getMethods()) {
            if (m.getName().equals(op.methodName())
                    && m.getParameterCount() == op.params().size()) {
                found = m;
                break;
            }
        }
        if (found == null) {
            throw new NoSuchPortedMethodException(receiver.getName() + " declares no method matching "
                    + op.key());
        }
        methods.put(op.key(), found);
        return found;
    }

    /** Thrown when the ported class exists but the operation does not. */
    static final class NoSuchPortedMethodException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NoSuchPortedMethodException(String message) {
            super(message);
        }
    }

    @Override
    public boolean supports(UOp op) {
        checkOpen();
        try {
            resolve(op);
            return true;
        } catch (NoSuchPortedClassException | NoSuchPortedMethodException e) {
            return false;
        }
    }

    @Override
    public String unsupportedReason(UOp op) {
        checkOpen();
        try {
            resolve(op);
            return "";
        } catch (NoSuchPortedClassException | NoSuchPortedMethodException e) {
            return "ported: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------ marshalling in

    /** Builds the ported {@code Value} corresponding to a plain-Java {@link UValue}. */
    static Value toPorted(UValue v) {
        Objects.requireNonNull(v, "v");
        try {
            switch (v.kind()) {
                case UREAL:     return new URealValue(v.asDouble(), v.uncertainty());
                case UINTEGER:  return new UIntegerValue(v.asInt(), v.uncertainty());
                case UBOOLEAN:  return UBooleanValue.valueOf(v.asBoolean(), v.probability());
                case USTRING:   return new UStringValue(v.asString(), v.confidence());
                case SBOOLEAN:  return new SBooleanValue.Builder()
                                        .belief(v.belief())
                                        .disbelief(v.disbelief())
                                        .uncertainty(v.uncertaintyMass())
                                        .agent(v.apriori())
                                        .build();
                case REAL:      return new RealValue(v.asDouble());
                case INTEGER:   return IntegerValue.valueOf(v.asInt());
                case BOOLEAN:   return BooleanValue.get(v.asBoolean());
                case STRING:    return new StringValue(v.asString());
                default:
                    throw new HarnessMarshallingException(
                            "the ported adapter cannot construct a value for kind " + v.kind()
                                    + " (" + v.canonical() + ")");
            }
        } catch (HarnessMarshallingException e) {
            throw e;
        } catch (RuntimeException e) {
            // A ported constructor rejecting an input happens BEFORE the operation under comparison
            // is entered, so it must never be scored against the reference. Same rule as the
            // historical side.
            throw new HarnessMarshallingException(
                    "a ported constructor threw while the harness was preparing " + v.canonical(), e);
        }
    }

    // ------------------------------------------------------------------ marshalling out

    /** Maps a value the PORTED code returned back to a {@link UValue}, observing its class. */
    static UValue fromPorted(Object raw) {
        if (raw == null) {
            return UValue.nullValue();
        }
        UValue out;
        if (raw instanceof URealValue r) {
            out = UValue.uReal(r.value(), r.uncertainty());
        } else if (raw instanceof UIntegerValue i) {
            out = UValue.uInteger(i.value(), i.uncertainty());
        } else if (raw instanceof UBooleanValue b) {
            out = UValue.uBoolean(b.value(), b.probability());
        } else if (raw instanceof UStringValue s) {
            out = UValue.uString(s.value(), s.confidence());
        } else if (raw instanceof RealValue r) {
            out = UValue.real(r.value());
        } else if (raw instanceof IntegerValue i) {
            out = UValue.integer(i.value());
        } else if (raw instanceof BooleanValue b) {
            out = UValue.bool(b.value());
        } else if (raw instanceof StringValue s) {
            out = UValue.string(s.value());
        } else if (raw instanceof org.tzi.use.uml.ocl.value.SequenceValue seq) {
            // Mirrors HistoricalOracle's SequenceValue branch exactly. Without it a sequence result
            // fell through to OPAQUE while the reference rendered SEQUENCE[...], and 21 of 22
            // UStringValue.uCharacters() rows came back DIFFER on IDENTICAL content -- a false
            // divergence produced by an asymmetry in the instrument, not by the port. Found by the
            // first real differential run; see docs/port2/stage-04.md.
            List<UValue> items = new ArrayList<>();
            for (Object element : seq) {
                items.add(fromPorted(element));
            }
            out = UValue.sequence(items);
        } else if (raw instanceof Boolean b) {
            out = UValue.bool(b);
        } else if (raw instanceof Integer i) {
            out = UValue.integer(i);
        } else if (raw instanceof Double d) {
            out = UValue.real(d);
        } else if (raw instanceof CharSequence c) {
            out = UValue.string(c.toString());
        } else {
            // Same renderer as the reference side -- see the class comment.
            out = UValue.opaque(raw.getClass().getName(),
                    HistoricalOracle.opaqueRepresentation(raw));
        }
        return out.observedFrom(raw);
    }

    // ------------------------------------------------------------------ invocation

    @Override
    public UValue invoke(UOp op, List<UValue> args) throws Throwable {
        checkOpen();
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(args, "args");
        if (args.size() != op.arity()) {
            throw new HarnessMarshallingException(
                    op.key() + " needs " + op.arity() + " values (receiver + " + op.params().size()
                            + " params) but got " + args.size());
        }
        Method method = resolve(op);
        Value receiver = toPorted(args.get(0));
        Class<?> receiverClass = portedClass(op.receiverType());
        if (!receiverClass.isInstance(receiver)) {
            throw new HarnessMarshallingException(
                    op.key() + " expects a ported receiver of " + receiverClass.getName()
                            + " but the supplied " + args.get(0).canonical() + " maps to "
                            + receiver.getClass().getName());
        }
        List<Object> marshalled = new ArrayList<>();
        for (int i = 1; i < args.size(); i++) {
            marshalled.add(marshalParam(op, method, i, args.get(i)));
        }
        Object raw;
        try {
            raw = method.invoke(receiver, marshalled.toArray());
        } catch (InvocationTargetException e) {
            // The PORTED code threw. That is a result, not a harness failure, so it is rethrown for
            // the sweep to compare against whatever the reference did.
            throw e.getCause() == null ? e : e.getCause();
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new HarnessMarshallingException(
                    "the harness could not invoke the ported " + op.key(), e);
        }
        if (method.getReturnType() == void.class) {
            return UValue.voidValue();
        }
        return fromPorted(raw);
    }

    private static Object marshalParam(UOp op, Method method, int index, UValue v) {
        Class<?> declared = method.getParameterTypes()[index - 1];
        if (declared == int.class || declared == Integer.class) {
            return v.asInt();
        }
        if (declared == double.class || declared == Double.class) {
            return v.asDouble();
        }
        if (declared == float.class || declared == Float.class) {
            return (float) v.asDouble();
        }
        if (declared == boolean.class || declared == Boolean.class) {
            return v.asBoolean();
        }
        if (declared == String.class) {
            return v.asString();
        }
        return toPorted(v);
    }
}
