package daikon.inv.unary.scalar;

import daikon.*;

import utilMDE.*;

import java.util.*;

public final class SingleFloatFactory {

  // Adds the appropriate new Invariant objects to the specified Invariants
  // collection.
  public static Vector instantiate(PptSlice ppt) {
    // System.out.println("Ppt arity " + ppt.arity() + " " + ppt.name() + " " + ppt);
    Assert.assertTrue(ppt.arity() == 1);
    VarInfo var = ppt.var_infos[0];
    // Assert.assertTrue(! var.rep_type.isArray());
    Assert.assertTrue(var.rep_type == ProglangType.DOUBLE);

    Vector result = new Vector();
    result.add(OneOfFloat.instantiate(ppt));
    result.add(LowerBoundFloat.instantiate(ppt));
    result.add(UpperBoundFloat.instantiate(ppt));
    result.add(NonZeroFloat.instantiate (ppt));
    result.addAll (RangeFloat.instantiate_all (ppt));
    return result;
  }

  private SingleFloatFactory() {
  }

}
