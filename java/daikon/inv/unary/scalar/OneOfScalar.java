// ***** This file is automatically generated from OneOf.java.jpp

package daikon.inv.unary.scalar;

import daikon.*;
import daikon.inv.*;
import daikon.derive.unary.*;
import daikon.inv.unary.scalar.*;
import daikon.inv.unary.sequence.*;
import daikon.inv.binary.sequenceScalar.*;
import daikon.inv.binary.twoSequence.SubSequence;

import utilMDE.*;

import java.util.*;
import java.io.*;

// States that the value is one of the specified values.

// This subsumes an "exact" invariant that says the value is always exactly
// a specific value.  Do I want to make that a separate invariant
// nonetheless?  Probably not, as this will simplify implication and such.

public final class OneOfScalar
  extends SingleScalar
  implements OneOf
{
  // We are Serializable, so we specify a version to allow changes to
  // method signatures without breaking serialization.  If you add or
  // remove fields, you should change this number to the current date.
  static final long serialVersionUID = 20020122L;

  // Variables starting with dkconfig_ should only be set via the
  // daikon.config.Configuration interface.
  /**
   * Boolean.  True iff OneOf invariants should be considered.
   **/
  public static boolean dkconfig_enabled = true;

  /**
   * Positive integer.  Specifies the maximum set size for this type
   * of invariant (x is one of 'n' items).
   **/

  public static int dkconfig_size = 3;

  // Probably needs to keep its own list of the values, and number of each seen.
  // (That depends on the slice; maybe not until the slice is cleared out.
  // But so few values is cheap, so this is quite fine for now and long-term.)

  private long [] elts;
  private int num_elts;

  /** Whether the variable's declared type is boolean. **/
  private boolean is_boolean;
  /** Whether the variable's declared type is hashcode. **/
  private boolean is_hashcode;

  OneOfScalar (PptSlice ppt) {
    super(ppt);

    elts = new long [dkconfig_size];

    num_elts = 0;

    is_boolean = (var().file_rep_type == ProglangType.BOOLEAN);
    is_hashcode = (var().file_rep_type == ProglangType.HASHCODE);

  }

  public static OneOfScalar  instantiate(PptSlice ppt) {
    if (!dkconfig_enabled) return null;
    return new OneOfScalar (ppt);
  }

  protected Object clone() {
    OneOfScalar  result = (OneOfScalar) super.clone();
    result.elts = (long []) elts.clone();

    result.num_elts = this.num_elts;

    result.is_boolean = this.is_boolean;
    result.is_hashcode = this.is_hashcode;

    return result;
  }

  public int num_elts() {
    return num_elts;
  }

  public Object elt() {
    if (num_elts != 1)
      throw new Error("Represents " + num_elts + " elements");

    // Not sure whether interning is necessary (or just returning an Integer
    // would be sufficient), but just in case...
    return Intern.internedLong(elts[0]);
  }

  private void sort_rep() {
    Arrays.sort(elts, 0, num_elts  );
  }

  public Object min_elt() {
    if (num_elts == 0)
      throw new Error("Represents no elements");
    sort_rep();

    // Not sure whether interning is necessary (or just returning an Integer
    // would be sufficient), but just in case...
    return Intern.internedLong(elts[0]);
  }

  public Object max_elt() {
    if (num_elts == 0)
      throw new Error("Represents no elements");
    sort_rep();

    // Not sure whether interning is necessary (or just returning an Integer
    // would be sufficient), but just in case...
    return Intern.internedLong(elts[num_elts-1]);
  }

  public long min_elt_long() {
    if (num_elts == 0)
      throw new Error("Represents no elements");
    sort_rep();
    return elts[0];
  }

  public long max_elt_long() {
    if (num_elts == 0)
      throw new Error("Represents no elements");
    sort_rep();
    return elts[num_elts-1];
  }

  // Assumes the other array is already sorted
  public boolean compare_rep(int num_other_elts, long [] other_elts) {
    if (num_elts != num_other_elts)
      return false;
    sort_rep();
    for (int i=0; i < num_elts; i++)
      if (elts[i] != other_elts[i]) // elements are interned
        return false;
    return true;
  }

  private String subarray_rep() {
    // Not so efficient an implementation, but simple;
    // and how often will we need to print this anyway?
    sort_rep();
    StringBuffer sb = new StringBuffer();
    sb.append("{ ");
    for (int i=0; i<num_elts; i++) {
      if (i != 0)
        sb.append(", ");
      sb.append(((Integer.MIN_VALUE <=  elts[i]  &&  elts[i]  <= Integer.MAX_VALUE) ? String.valueOf( elts[i] ) : (String.valueOf( elts[i] ) + "L")));
    }
    sb.append(" }");
    return sb.toString();
  }

  public String repr() {
    return "OneOfScalar"  + varNames() + ": "
      + "falsified=" + falsified
      + ", num_elts=" + num_elts
      + ", elts=" + subarray_rep();
  }

  public String format_using(OutputFormat format) {
    if (format == OutputFormat.DAIKON) {
      return format_daikon();
    } else if (format == OutputFormat.JAVA) {
      return format_java();
    } else if (format == OutputFormat.IOA) {
      return format_ioa();
    } else if (format == OutputFormat.SIMPLIFY) {
      return format_simplify();
    } else if (format == OutputFormat.ESCJAVA) {
      return format_esc();
    } else if (format == OutputFormat.JML) {
      return format_jml();
    } else {
      return format_unimplemented(format);
    }
  }

  public String format_daikon() {
    String varname = var().name.name() ;
    if (num_elts == 1) {

      if (is_boolean) {
        Assert.assertTrue((elts[0] == 0) || (elts[0] == 1));
        return varname + " == " + ((elts[0] == 0) ? "false" : "true");
      } else if (is_hashcode) {
        if (elts[0] == 0) {
          return varname + " == null";
        } else {
          return varname + " has only one value"
            // + " (hashcode=" + elts[0] + ")"
            ;
        }
      } else {
        return varname + " == " + ((Integer.MIN_VALUE <=  elts[0]  &&  elts[0]  <= Integer.MAX_VALUE) ? String.valueOf( elts[0] ) : (String.valueOf( elts[0] ) + "L")) ;
      }

    } else {
      return varname + " one of " + subarray_rep();
    }
  }

  /*
    public String format_java() {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < num_elts; i++) {
    sb.append (" || (" + var().name.java_name()  + " == " +  ((Integer.MIN_VALUE <=  elts[i]  &&  elts[i]  <= Integer.MAX_VALUE) ? String.valueOf( elts[i] ) : (String.valueOf( elts[i] ) + "L"))   );
    sb.append (")");
    }
    // trim off the && at the beginning for the first case
    return sb.toString().substring (4);
    }
  */

  public String format_java() {
    //have to take a closer look at this!

    String varname = var().name.java_name();

    String result;

    if (is_boolean) {
      Assert.assertTrue(num_elts == 1);
      Assert.assertTrue((elts[0] == 0) || (elts[0] == 1));
      result = varname + " == " + ((elts[0] == 0) ? "false" : "true");
    } else if (is_hashcode) {
      if (num_elts == 2) {
        return "true";          // one elt is null, the other is non-null
      } else if (elts[0] == 0) {
        result = varname + " == null";
      } else {
        result = varname + " != null";
          // varname + " has only one value"
          // + " (hashcode=" + elts[0] + ")"
          ;
      }
    } else {
      result = "";
      for (int i=0; i<num_elts; i++) {
        if (i != 0) { result += " || "; }
        result += varname + " == " + ((Integer.MIN_VALUE <=  elts[i]  &&  elts[i]  <= Integer.MAX_VALUE) ? String.valueOf( elts[i] ) : (String.valueOf( elts[i] ) + "L")) ;
      }
    }

    return result;
  }

  /* IOA */
  public String format_ioa() {

    String varname = var().name.ioa_name();

    String result;

    if (is_boolean) {
      Assert.assertTrue(num_elts == 1);
      Assert.assertTrue((elts[0] == 0) || (elts[0] == 1));
      result = varname + " = " + ((elts[0] == 0) ? "false" : "true");
    } else if (is_hashcode) {
      Assert.assertTrue(num_elts == 1);
      if (elts[0] == 0) {
        result = varname + " = null";
      } else {
        result = varname + " = {one value}";
      }
    } else {
      result = "";
      for (int i=0; i<num_elts; i++) {
        if (i != 0) { result += " \\/ "; }
        result += "(" + varname + " = " + ((Integer.MIN_VALUE <=  elts[i]  &&  elts[i]  <= Integer.MAX_VALUE) ? String.valueOf( elts[i] ) : (String.valueOf( elts[i] ) + "L"))  + ")";
      }
    }

    return result;
  }

  public String format_esc() {

    String varname = var().name.esc_name();

    String result;

    if (is_boolean) {
      Assert.assertTrue(num_elts == 1);
      Assert.assertTrue((elts[0] == 0) || (elts[0] == 1));
      result = varname + " == " + ((elts[0] == 0) ? "false" : "true");
    } else if (is_hashcode) {
      if (num_elts == 1) {
        if (elts[0] == 0) {
          result = varname + " == null";
        } else {
          result = varname + " != null";
          // varname + " has only one value"
          // + " (hashcode=" + elts[0] + ")"
          ;
        }
      } else {
        // add_modified allows two elements iff one is null
        Assert.assertTrue(num_elts == 2);
        Assert.assertTrue(elts[0] == 0);
        Assert.assertTrue(elts[1] != 0);
        return format_unimplemented(OutputFormat.ESCJAVA); // "needs to be implemented"
      }
    } else {
      result = "";
      for (int i=0; i<num_elts; i++) {
        if (i != 0) { result += " || "; }
        result += varname + " == " + ((Integer.MIN_VALUE <=  elts[i]  &&  elts[i]  <= Integer.MAX_VALUE) ? String.valueOf( elts[i] ) : (String.valueOf( elts[i] ) + "L")) ;
      }
    }

    return result;
  }

  public String format_jml() {

    String varname = var().name.jml_name();

    String result;

    if (is_boolean) {
      Assert.assertTrue(num_elts == 1);
      Assert.assertTrue((elts[0] == 0) || (elts[0] == 1));
      result = varname + " == " + ((elts[0] == 0) ? "false" : "true");
    } else if (is_hashcode) {
      if (num_elts == 2) {
        return "true";          // one elt is null, the other is non-null
      } else if (elts[0] == 0) {
        result = varname + " == null";
      } else {
        result = varname + " != null";
          // varname + " has only one value"
          // + " (hashcode=" + elts[0] + ")"
          ;
      }
    } else {
      result = "";
      for (int i=0; i<num_elts; i++) {
        if (i != 0) { result += " || "; }
        result += varname + " == " + ((Integer.MIN_VALUE <=  elts[i]  &&  elts[i]  <= Integer.MAX_VALUE) ? String.valueOf( elts[i] ) : (String.valueOf( elts[i] ) + "L")) ;
      }
    }

    return result;
  }

  public String format_simplify() {

    String varname = var().name.simplify_name();

    String result;

    if (is_boolean) {
      Assert.assertTrue(num_elts == 1);
      Assert.assertTrue((elts[0] == 0) || (elts[0] == 1));
      result = "(EQ " + varname + " " + ((elts[0] == 0) ? "|@false|" : "|@true|") + ")";
    } else if (is_hashcode) {
      if (num_elts == 1) {
        result = "(EQ " + varname + " " + ((elts[0] == 0) ? "null" : ("|hash_" + elts[0] + "|")) + ")";
      } else {
        // add_modified allows two elements iff one is null
        Assert.assertTrue(num_elts == 2);
        Assert.assertTrue(elts[0] == 0);
        Assert.assertTrue(elts[1] != 0);
        result = "(OR (EQ " + varname + " null) (EQ " + varname + "|hash_" + elts[1] + "|))";
      }
    } else {
      result = "";
      for (int i=0; i<num_elts; i++) {
        result += " (EQ " + varname + " " + ((Integer.MIN_VALUE <=  elts[i]  &&  elts[i]  <= Integer.MAX_VALUE) ? String.valueOf( elts[i] ) : (String.valueOf( elts[i] ) + "L"))  + ")";
      }
      if (num_elts > 1) {
        result = "(OR" + result + ")";
      } else {
        // chop leading space
        result = result.substring(1);
      }
    }

    return result;
  }

  public void add_modified(long  v, int count) {

    for (int i=0; i<num_elts; i++)
      if (elts[i] == v) {

        return;

      }
    if (num_elts == dkconfig_size) {
      destroyAndFlow();
      return;
    }

    if ((is_boolean && (num_elts == 1))
        || (is_hashcode && (num_elts == 2))) {
      destroyAndFlow();
      return;
    }
    if (is_hashcode && (num_elts == 1)) {
      // Permit two object values only if one of them is null
      if ((elts[0] != 0) && (v != 0)) {
        destroyAndFlow();
        return;
      }
    }

    // We are significantly changing our state (not just zeroing in on
    // a constant), so we have to flow a copy before we do so.  We even
    // need to clone if this has 0 elements becuase otherwise, lower
    // ppts will get versions of this with multiple elements once this is
    // expanded.
    cloneAndFlow();

    elts[num_elts] = v;
    num_elts++;

  }

  protected double computeProbability() {
    // This is not ideal.
    if (num_elts == 0) {
      return Invariant.PROBABILITY_UNJUSTIFIED;
    } else if (is_hashcode && (num_elts > 1)) {
      // This should never happen
      return Invariant.PROBABILITY_UNJUSTIFIED;
    } else {
      return Invariant.PROBABILITY_JUSTIFIED;
    }
  }

  // Use isObviousDerived since some isObviousImplied methods already exist.
  public boolean isObviousDerived() {
    // Static constants are necessarily OneOf precisely one value.
    // This removes static constants from the output, which might not be
    // desirable if the user doesn't know their actual value.
    if (var().isStaticConstant()) {
      Assert.assertTrue(num_elts <= 1);
      return true;
    }
    return super.isObviousDerived();
  }

  public boolean isObviousImplied() {
    VarInfo v = var();
    if (v.isDerived() && (v.derived instanceof SequenceLength)) {
      SequenceLength sl = (SequenceLength) v.derived;
      if (sl.shift != 0) {
        return true;
      }
    }

    // For every EltOneOf  at this program point, see if this variable is
    // an obvious member of that sequence.
    PptTopLevel parent = ppt.parent;
    for (Iterator itor = parent.invariants_iterator(); itor.hasNext(); ) {
      Invariant inv = (Invariant) itor.next();
      if ((inv instanceof EltOneOf) && inv.enoughSamples()) {
        VarInfo v1 = var();
        VarInfo v2 = inv.ppt.var_infos[0];
        // System.out.println("isObviousImplied: calling  Member.isObviousMember(" + v1.name + ", " + v2.name + ")");
        // Don't use isEqualToObviousMember:  that is too subtle
        // and eliminates desirable invariants such as "return == null".
        if (Member.isObviousMember(v1, v2)) {
          EltOneOf  other = (EltOneOf) inv;
          if (num_elts == other.num_elts()) {
            sort_rep();
            if (other.compare_rep(num_elts, elts)) {
              // System.out.println("isObviousImplied true");
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  public boolean isSameFormula(Invariant o)
  {
    OneOfScalar  other = (OneOfScalar) o;
    if (num_elts != other.num_elts)
      return false;
    if (num_elts == 0 && other.num_elts == 0)
      return true;

    sort_rep();
    other.sort_rep();

    // All nonzero hashcode values should be considered equal to each other
    //
    // Examples:
    // inv1  inv2  result
    // ----  ----  ------
    // 19    0     false
    // 19    22    true
    // 0     0     true

    if (is_hashcode && other.is_hashcode) {
      if (num_elts == 1 && other.num_elts == 1) {
        return ((elts[0] == 0 && other.elts[0] == 0) ||
                (elts[0] != 0 && other.elts[0] != 0));
      } else if (num_elts == 2 && other.num_elts == 2) {
        // add_modified allows two elements iff one is null
        Assert.assertTrue(elts[0] == 0);
        Assert.assertTrue(other.elts[0] == 0);
        Assert.assertTrue(elts[1] != 0);
        Assert.assertTrue(other.elts[1] != 0);

        // Since we know the first elements of each invariant are
        // zero, and the second elements are nonzero, we can immediately
        // return true
        return true;
      } else {
        return false;
      }
    }

    for (int i=0; i < num_elts; i++)
      if (elts[i] != other.elts[i]) // elements are interned
        return false;

    return true;
  }

  public boolean isExclusiveFormula(Invariant o)
  {
    if (o instanceof OneOfScalar) {
      OneOfScalar  other = (OneOfScalar) o;

      for (int i=0; i < num_elts; i++) {
        for (int j=0; j < other.num_elts; j++) {
          if (elts[i] == other.elts[j]) // elements are interned
            return false;
        }
      }
      return true;
    }

    // Many more checks can be added here:  against nonzero, modulus, etc.
    if ((o instanceof NonZero) && (num_elts == 1) && (elts[0] == 0)) {
      return true;
    }
    long  elts_min = Long.MAX_VALUE;
    long  elts_max = Long.MIN_VALUE;
    for (int i=0; i < num_elts; i++) {
      elts_min = Math.min(elts_min, elts[i]);
      elts_max = Math.max(elts_max, elts[i]);
    }
    if ((o instanceof LowerBound) && (elts_max < ((LowerBound)o).core.min1))
      return true;
    if ((o instanceof UpperBound) && (elts_min > ((UpperBound)o).core.max1))
      return true;

    return false;
  }

  // OneOf invariants that indicate a small set of possible values are
  // uninteresting.  OneOf invariants that indicate exactly one value
  // are interesting.
  public boolean isInteresting() {
    if (num_elts() > 1) {
      return false;
    } else {
      return true;
    }
  }

  // Look up a previously instantiated invariant.
  public static OneOfScalar  find(PptSlice ppt) {
    Assert.assertTrue(ppt.arity == 1);
    for (Iterator itor = ppt.invs.iterator(); itor.hasNext(); ) {
      Invariant inv = (Invariant) itor.next();
      if (inv instanceof OneOfScalar)
        return (OneOfScalar) inv;
    }
    return null;
  }

  // Interning is lost when an object is serialized and deserialized.
  // Manually re-intern any interned fields upon deserialization.
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    for (int i=0; i < num_elts; i++)
      elts[i] = Intern.intern(elts[i]);
  }

}
