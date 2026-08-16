package org.tzi.use.uncertainty.differential;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Names one operation on one U-type, in a form both sides of the differential can dispatch on.
 *
 * <p>{@code receiverType} is the simple name of the historical class the operation is declared on
 * ({@code URealValue}, {@code UIntegerValue}, {@code UBooleanValue}, {@code UStringValue}).
 * {@code params} describes the declared parameter types, because the historical API mixes
 * {@code Value} parameters with raw {@code int}/{@code double}/{@code float} ones
 * (e.g. {@code UStringValue.uSubstring(int,int)}, {@code UBooleanValue.equalsC(Value,double)},
 * {@code URealValue.power(float)}).
 *
 * <p>Test-scoped. Not part of the product.
 */
public final class UOp {

    /** Declared parameter type of a historical operation. */
    public enum ParamKind {
        /** {@code org.tzi.use.uml.ocl.value.Value} — the argument is itself a value. */
        VALUE,
        /** primitive {@code int}. */
        INT,
        /** primitive {@code double}. */
        DOUBLE,
        /** primitive {@code float}. */
        FLOAT
    }

    private final String receiverType;
    private final String methodName;
    private final List<ParamKind> params;

    private UOp(String receiverType, String methodName, List<ParamKind> params) {
        this.receiverType = receiverType;
        this.methodName = methodName;
        this.params = Collections.unmodifiableList(params);
    }

    public static UOp of(String receiverType, String methodName, ParamKind... params) {
        return new UOp(receiverType, methodName, Arrays.asList(params));
    }

    /** A no-argument operation such as {@code URealValue.sqrt()}. */
    public static UOp unary(String receiverType, String methodName) {
        return of(receiverType, methodName);
    }

    /** A one-{@code Value}-argument operation such as {@code URealValue.add(Value)}. */
    public static UOp binary(String receiverType, String methodName) {
        return of(receiverType, methodName, ParamKind.VALUE);
    }

    public String receiverType() {
        return receiverType;
    }

    public String methodName() {
        return methodName;
    }

    public List<ParamKind> params() {
        return params;
    }

    /** Total number of {@link UValue} slots the sweep must supply: receiver plus declared params. */
    public int arity() {
        return 1 + params.size();
    }

    /** Stable identifier used as the {@code operation} column of the report. */
    public String key() {
        StringBuilder sb = new StringBuilder(receiverType).append('.').append(methodName).append('(');
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(params.get(i).name().toLowerCase());
        }
        return sb.append(')').toString();
    }

    @Override
    public String toString() {
        return key();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UOp && key().equals(((UOp) o).key());
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }
}
