package org.tzi.use.smt.reconstruct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigInteger;
import java.util.List;
import org.junit.Test;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.solver.SmtValue;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.StringValue;

public class SmtValueDecoderTest {

  private static final AttributeDomain NO_DOMAIN =
      new AttributeDomain("C", "a", null, List.of(), null, null);

  @Test
  public void aStringAttributeDecodesTheIntAsAnIndexIntoTheDomain() {
    AttributeDomain domain =
        new AttributeDomain("User", "name", null, List.of("Ada", "Bob"), null, null);

    StringValue decoded =
        (StringValue)
            SmtValueDecoder.decode(
                new SmtValue.Int(BigInteger.ONE), TypeFactory.mkString(), domain);

    assertEquals("Bob", decoded.value());
  }

  /** Proves STRING and INTEGER are told apart by the real declared type, not by SMT sort alone. */
  @Test
  public void anIntegerAttributeDecodesTheIntAsTheLiteralValueNotAnIndex() {
    IntegerValue decoded =
        (IntegerValue)
            SmtValueDecoder.decode(
                new SmtValue.Int(BigInteger.valueOf(1995)), TypeFactory.mkInteger(), NO_DOMAIN);

    assertEquals(1995, decoded.value());
  }

  @Test
  public void aRealAttributeDecodesAnExactRationalToItsDoubleValue() {
    RealValue decoded =
        (RealValue)
            SmtValueDecoder.decode(
                new SmtValue.Rational(BigInteger.valueOf(3), BigInteger.valueOf(2)),
                TypeFactory.mkReal(),
                NO_DOMAIN);

    assertEquals(1.5, decoded.value(), 0.0);
  }

  @Test
  public void aBooleanAttributeDecodesDirectly() {
    BooleanValue decoded =
        (BooleanValue)
            SmtValueDecoder.decode(new SmtValue.Bool(true), TypeFactory.mkBoolean(), NO_DOMAIN);

    assertEquals(true, decoded.value());
  }

  @Test
  public void decodingAStringAttributeFromANonIntegerModelValueFailsClosed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SmtValueDecoder.decode(new SmtValue.Bool(true), TypeFactory.mkString(), NO_DOMAIN));
  }
}
