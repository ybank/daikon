package daikon.inv.binary.twoScalar;

import daikon.*;
import daikon.inv.*;
import daikon.inv.unary.sequence.*;
import daikon.inv.unary.scalar.*;
import daikon.inv.binary.sequenceScalar.*;
import daikon.inv.binary.twoSequence.*;
import daikon.derive.*;
import daikon.derive.unary.*;

import utilMDE.*;

import java.util.*;

// *****
// Do not edit this file directly:
// it is automatically generated from IntComparisons.java.jpp
// *****

// Also see NonEqual
public final class IntEqual  extends TwoScalar implements Comparison  {

  // Variables starting with dkconfig_ should only be set via the
  // daikon.config.Configuration interface.
  public static boolean dkconfig_enabled = true;

  final static boolean debugIntEqual  = false;

  protected IntEqual (PptSlice ppt) {
    super(ppt);
  }

  public static IntEqual  instantiate(PptSlice ppt) {
    if (!dkconfig_enabled) return null;

    VarInfo var1 = ppt.var_infos[0];
    VarInfo var2 = ppt.var_infos[1];
    VarInfo seqvar1 = var1.isDerivedSequenceMember();
    VarInfo seqvar2 = var2.isDerivedSequenceMember();

    if (debugIntEqual  || ppt.debugged) {
      System.out.println("IntEqual.instantiate(" + ppt.name + ")"
                         + ", seqvar1=" + seqvar1
                         + ", seqvar2=" + seqvar2);
    }

    { // Tests involving sequence lengths.

      SequenceLength sl1 = null;
      if (var1.isDerived() && (var1.derived instanceof SequenceLength))
        sl1 = (SequenceLength) var1.derived;
      SequenceLength sl2 = null;
      if (var2.isDerived() && (var2.derived instanceof SequenceLength))
        sl2 = (SequenceLength) var2.derived;

      // Avoid "size(a)-1 cmp size(b)-1"; use "size(a) cmp size(b)" instead.
      if ((sl1 != null) && (sl2 != null)
          && ((sl1.shift == sl2.shift) && (sl1.shift != 0) || (sl2.shift != 0))) {
        // "size(a)-1 cmp size(b)-1"; should just use "size(a) cmp size(b)"
        return null;
      }
    }

    boolean only_eq = false;
    boolean obvious_lt = false;
    boolean obvious_gt = false;
    boolean obvious_le = false;
    boolean obvious_ge = false;

    // Commented out temporarily.
    if (false && (seqvar1 != null) && (seqvar2 != null)) {
      Derivation deriv1 = var1.derived;
      Derivation deriv2 = var2.derived;
      boolean min1 = (deriv1 instanceof SequenceMin);
      boolean max1 = (deriv1 instanceof SequenceMax);
      boolean min2 = (deriv2 instanceof SequenceMin);
      boolean max2 = (deriv2 instanceof SequenceMax);
      VarInfo super1 = seqvar1.isDerivedSubSequenceOf();
      VarInfo super2 = seqvar2.isDerivedSubSequenceOf();

      if (debugIntEqual  || ppt.debugged) {
        System.out.println("IntEqual.instantiate: "
                           + "min1=" + min1
                           + ", max1=" + max1
                           + ", min2=" + min2
                           + ", max2=" + max2
                           + ", super1=" + super1
                           + ", super2=" + super2
                           + ", iom(var2, seqvar1)=" + Member.isObviousMember(var2, seqvar1)
                           + ", iom(var1, seqvar2)=" + Member.isObviousMember(var1, seqvar2));
      }
      if (seqvar1 == seqvar2) {
        // Both variables are derived from the same sequence.  The
        // invariant is obvious as soon as it's nonequal, because "all
        // elements equal" will be reported elsewhere.
        if (min1 || max2)
          obvious_lt = true;
        else if (max1 || min2)
          obvious_gt = true;
      } else if ((min1 || max1) && Member.isObviousMember(var2, seqvar1)) {
        if (min1) {
          obvious_le = true;
        } else if (max1) {
          obvious_ge = true;
        }
      } else if ((min2 || max2) && Member.isObviousMember(var1, seqvar2)) {
        if (min2) {
          obvious_ge = true;
        } else if (max2) {
          obvious_le = true;
        }
      } else if (((min1 && max2) || (max1 && min2))
                 && (super1 != null) && (super2 != null) && (super1 == super2)
                 && VarInfo.seqs_overlap(seqvar1, seqvar2)) {
        // If the sequences overlap, then clearly the min of either is no
        // greater than the max of the other.
        if (min1 && max2) {
          obvious_le = true;
          // System.out.println("obvious_le: " + var1.name + " " + var2.name);
        } else if (max1 && min2) {
          obvious_ge = true;
          // System.out.println("obvious_ge: " + var1.name + " " + var2.name);
        }
      }
    }

    return new IntEqual (ppt);

  }

  // Still TODO
  protected Invariant resurrect_done(int[] permutation) {
    throw new UnsupportedOperationException();
  }

  // Look up a previously instantiated IntEqual  relationship.
  // Should this implementation be made more efficient?
  public static IntEqual  find(PptSlice ppt) {
    Assert.assert(ppt.arity == 2);
    for (Iterator itor = ppt.invs.iterator(); itor.hasNext(); ) {
      Invariant inv = (Invariant) itor.next();
      if (inv instanceof IntEqual )
        return (IntEqual ) inv;
    }
    return null;
  }

  public String repr() {
    return "IntEqual"  + varNames();
  }

  public String format() {
    return var1().name.name() + " == " + var2().name.name();
  }

  public String format_esc() {
    return var1().name.esc_name() + " == " + var2().name.esc_name();
  }

  /* IOA */
  public String format_ioa(String classname) {

    String comparator = "=";

    return var1().name.ioa_name(classname)+" "+comparator+" "+var2().name.ioa_name(classname);
  }

  public String format_simplify() {

    String comparator = "EQ";

    return "(" + comparator + " " + var1().name.simplify_name() + " " + var2().name.simplify_name() + ")";
  }

  public void add_modified(long v1, long v2, int count) {
    // if (ppt.debugged) {
    //   System.out.println("IntEqual"  + ppt.varNames() + ".add_modified("
    //                      + v1 + "," + v2 + ", count=" + count + ")");
    // }
    if (!(v1 ==  v2)) {
      destroy();
      return;
    }
  }

  // This is very tricky, because whether two variables are equal should
  // presumably be transitive, but it's not guaranteed to be so when using
  // this method and not dropping out all variables whose values are ever
  // missing.
  public double computeProbability() {
    if (no_invariant) {
      return Invariant.PROBABILITY_NEVER;
    }
    // Should perhaps check number of samples and be unjustified if too few
    // samples.

    // It's an equality invariant.  I ought to use the actual ranges somehow.
    // Actually, I can't even use this .5 test because it can make
    // equality non-transitive.
    // return Math.pow(.5, ppt.num_values());
    return Invariant.PROBABILITY_JUSTIFIED;

  }

  // For Comparison interface
  public double eq_probability() {
    if (isExact())
      return computeProbability();
    else
      return Invariant.PROBABILITY_NEVER;
  }

  public boolean isExact() {

    return true;

  }

  // // Temporary, for debugging
  // public void destroy() {
  //   if (debugIntEqual  || ppt.debugged) {
  //     System.out.println("IntEqual.destroy(" + ppt.name + ")");
  //     System.out.println(repr());
  //     (new Error()).printStackTrace();
  //   }
  //   super.destroy();
  // }

  public void add(long v1, long v2, int mod_index, int count) {
    if (ppt.debugged) {
      System.out.println("IntEqual"  + ppt.varNames() + ".add("
                         + v1 + "," + v2
                         + ", mod_index=" + mod_index + ")"
                         + ", count=" + count + ")");
    }
    super.add(v1, v2, mod_index, count);
  }

  public boolean isSameFormula(Invariant other)
  {
    return true;
  }

  public boolean isExclusiveFormula(Invariant other)
  {
    // Also ought to check against LinearBinary, etc.

    if ((other instanceof IntLessThan) || (other instanceof IntGreaterThan))
      return true;

    return false;
  }

  public boolean isObviousImplied() {
    VarInfo var1 = ppt.var_infos[0];
    VarInfo var2 = ppt.var_infos[1];

    return ((var1.name instanceof VarInfoName.Add) && (var2.name instanceof VarInfoName.Add) &&
	      ((((VarInfoName.Add) var1.name).amount) == (((VarInfoName.Add) var2.name).amount)));

  } // isObviousImplied
}

