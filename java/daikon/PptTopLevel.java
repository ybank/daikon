package daikon;

import daikon.derive.*;
import daikon.derive.unary.*;
import daikon.derive.binary.*;
import daikon.inv.*;
import daikon.inv.filter.*;
import daikon.inv.unary.scalar.*;
import daikon.inv.unary.string.*;
import daikon.inv.unary.sequence.*;
import daikon.inv.unary.stringsequence.*;
import daikon.inv.ternary.threeScalar.*;
import daikon.simplify.*;
import daikon.split.*;
import daikon.split.misc.*;

import java.io.*;
import java.util.*;

import org.apache.oro.text.regex.*;
import org.apache.log4j.Category;

import utilMDE.*;


// All information about a single program point.
// A Ppt may also represent just part of the data: a disjunction (see
// PptConditional).
// This probably doesn't do any direct computation, instead deferring that
// to its views that are slices and that actually contain the invariants.

public class PptTopLevel extends Ppt {

  /**
   * Logging Category.
   **/
  
  public static final Category debug =
    Category.getInstance (PptTopLevel.class.getName());
  public static final Category debugEqualTo =
    Category.getInstance (PptTopLevel.class.getName() + "equalTo");
  public static final Category debugAddImplications =
    Category.getInstance (PptTopLevel.class.getName() + "addImplications");

  // Do we need both a num_tracevars for the number of variables in the
  // tracefile and a num_non_derived_vars for the number of variables
  // actually passed off to this Ppt?  The ppt wouldn't use num_tracevars,
  // but it would make sense to store it here anyway.

  // These values are -1 if not yet set (can that happen?).
  // Invariant:  num_declvars == num_tracevars + num_orig_vars
  int num_declvars;             // number of variables in the decl file
  int num_tracevars;            // number of variables in the trace file
  int num_orig_vars;            // number of _orig vars
  int num_static_constant_vars; // these don't appear in the trace file

  // Indicates which derived variables have been introduced.
  // First number: invariants are computed up to this index, non-inclusive.
  // Remaining numbers: values have been derived from up to these indices.
  // Invariant:
  //   len(var_infos) >= derivation_index[0] >= derivation_index[1] >= ...
  int[] derivation_indices = new int[derivation_passes+1];

  private transient VarValuesOrdered values;
  // these are used only when values is null
  private int values_num_samples;
  private int values_num_mod_non_missing_samples;
  private int values_num_values;
  private String values_tuplemod_samples_summary;

  // Assumption: The "depends on" graph is acyclic
  // (the graph edges are: <this, (entry_ppt U controlling_ppts)>).
  // This is necessary because we search the graph in isWorthPrinting.

  public PptTopLevel entry_ppt;        	// null if this isn't an exit point
  public Vector exit_ppts = new Vector(1); // elts are PptTopLevel objects;
                                // this is set for entry program points
  public PptTopLevel combined_exit;	// null if this isn't a line-numbered exit point
  public int[] combined_exit_var_indices; // null if combined_exit == null

  // PptTopLevel has any number of 'controlling' ppts.  Any invariants
  // which exist in the controlling ppts are necessarily true in the
  // controlled ppts, and therefore may be suppressed in the output.
  // For example, public methods are controlled by object invariants,
  // and conditional points are controlled by the unconditional
  // parent point.  This set contains only the immediate controllers,
  // not the transitive closure of all controllers.
  public Set controlling_ppts = new HashSet(); // elements are PptTopLevel objects

  public PptSlice0 implication_view = new PptSlice0(this);

  // The set of redundant_invs is filled in by the below method
  // mark_implied_via_simplify.  Contents are either Invariant
  // objects, or, in the case of Equality invariants, the canonical
  // VarInfo for the equality.
  public Set redundant_invs = new HashSet();

  public PptTopLevel(String name, VarInfo[] var_infos) {
    super(name);
    this.var_infos = var_infos;
    int val_idx = 0;
    num_static_constant_vars = 0;
    for (int i=0; i<var_infos.length; i++) {
      VarInfo vi = var_infos[i];
      vi.varinfo_index = i;
      if (vi.is_static_constant) {
        vi.value_index = -1;
        num_static_constant_vars++;
      } else {
        vi.value_index = val_idx;
        val_idx++;
      }
      vi.ppt = this;
    }
    for (int i=0; i<var_infos.length; i++) {
      VarInfo vi = var_infos[i];
      Assert.assert((vi.value_index == -1) || (!vi.is_static_constant));
    }

    values = new VarValuesOrdered();
    views = new HashSet();
    views_cond = new Vector();

    num_declvars = var_infos.length;
    num_tracevars = val_idx;
    num_orig_vars = 0;
    Assert.assert(num_static_constant_vars == num_declvars - num_tracevars);
    // System.out.println("Created PptTopLevel " + name + ": "
    //                    + "num_static_constant_vars=" + num_static_constant_vars
    //                    + ",num_declvars=" + num_declvars
    //                    + ",num_tracevars=" + num_tracevars);
  }

  // // This is used when merging two sets of data, to create Ppts that were
  // // missing in one of them.
  // PptTopLevel(PptTopLevel other) {
  //   name = other.name;
  //   var_infos = other.var_infos;
  //   derivation_indices = new int[other.derivation_indices.length];
  //   System.arraycopy(other.derivation_indices, 0, derivation_indices, 0, derivation_indices.length);
  //   values = new VarValuesOrdered();
  //   // views = new WeakHashMap();
  //   views = new HashSet();
  // }



  // // Strangely, this modifies "other" but not "this".  That looks wrong.
  // // Perhaps this should operate over VarValues objects or some such?
  // void merge(PptTopLevel other) {
  //   // ensure that the two program points are compatible
  //   Assert.assert(compatible(other));
  //
  //   Set other_entries = other.values.entrySet();
  //   for (Iterator itor = other_entries.iterator() ; itor.hasNext() ;) {
  //     Map.Entry other_entry = (Map.Entry) itor.next();
  //     ValueTuple value_tuple = (ValueTuple) other_entry.getKey();
  //     int new_count = (values.get(value_tuple)
  //                      + VarValues.getValue(other_entry));
  //     // equivalent but less efficient:  values.put(value_tuple, new_count);
  //     VarValues.setValue(other_entry, new_count);
  //   }


  ///////////////////////////////////////////////////////////////////////////
  /// Accessing data
  ///

  public int num_vars() {
    return var_infos.length;
  }

  public int num_array_vars() {
    int num_arrays=0;
    for (int i=0; i<var_infos.length; i++)
      if (var_infos[i].rep_type.isArray())
	num_arrays++;
    return num_arrays;
  }

  Iterator var_info_iterator() {
    return Arrays.asList(var_infos).iterator();
  }
  public PptTopLevel entry_ppt() {
    return entry_ppt;
  }

  // These accessors are for abstract methods declared in Ppt
  public int num_samples() {
    return (values == null) ? values_num_samples : values.num_samples(); }
  public boolean has_samples() {
    if (num_samples() > 0)
      return true;
    if (ppt_name.isCombinedExitPoint()) {
      Vector exits = entry_ppt.exit_ppts;
      for (int i=0; i<exits.size(); i++) {
        PptTopLevel exit = (PptTopLevel) exits.elementAt(i);
        // System.out.println("Recursive call from " + name + " via " + entry_ppt.name + ": " + exit.name);
        if ((exit != this) && exit.has_samples()) {
          return true;
        }
      }
    }
    return false;
  }
  public int num_mod_non_missing_samples() {
      return ((values == null)
              ? values_num_mod_non_missing_samples
              : values.num_mod_non_missing_samples()); }
  // WARNING!  This is the number of distinct ValueTuple objects,
  // which can be as much as 2^arity times as many as the number of
  // distinct tuples of values.
  public int num_values() {
    return (values == null) ? values_num_values : values.num_values(); }
  // public int num_missing() {
  //   return (values == null) ? value_num_missing : values.num_missing; }
  public String tuplemod_samples_summary() {
      return ((values == null)
              ? values_tuplemod_samples_summary
              : values.tuplemod_samples_summary());
  }
  public void set_values_null() {
    values_num_samples = num_samples();
    values_num_mod_non_missing_samples = num_mod_non_missing_samples();
    values_num_values = num_values();
    values_tuplemod_samples_summary = tuplemod_samples_summary();
    values = null;
  }



  // This method is added as somewhat of a hack for Melissa's gui.  In the
  // gui, PptTopLevel are stored as nodes in a tree.  Swing obtains the
  // string to display in the actual JTree by calling toString().
  public String toString() {
    PptName pptName = new PptName( name );
    if (pptName.isObjectInstanceSynthetic())   // display "MyClassName : OBJECT"
	return pptName.getFullClassName() + " : " + FileIO.object_suffix;
    else if (pptName.isClassStaticSynthetic()) // display "MyClassName : CLASS"
	return pptName.getFullClassName() + " : " + FileIO.class_static_suffix;
    else			               // only display "EXIT184"
	return pptName.getPoint();
  }


  boolean compatible(Ppt other) {
    // This insists that the var_infos lists are identical.  The Ppt
    // copy constructor does reuse the var_infos field.
    return (var_infos == other.var_infos);
  }


  ///////////////////////////////////////////////////////////////////////////
  /// Adding variables
  ///

  // Some of this should perhaps be moved up into Ppt.

  // I'm not using a Vector for the var_infos field, even though that would
  // simplify adding new elements, because I want static typechecking and I
  // don't want the overheads of lots of casts and of extra space for
  // Vector objects.

  private void addVarInfo(VarInfo vi) {
    VarInfo[] vis = new VarInfo[] { vi };
    addVarInfos(vis);
  }

  private void addVarInfos(VarInfo[] vis) {
    if (vis.length == 0)
      return;
    int old_length = var_infos.length;
    VarInfo[] new_var_infos = new VarInfo[var_infos.length + vis.length];
    System.arraycopy(var_infos, 0, new_var_infos, 0, old_length);
    System.arraycopy(vis, 0, new_var_infos, old_length, vis.length);
    for (int i=old_length; i<new_var_infos.length; i++) {
      VarInfo vi = new_var_infos[i];
       vi.varinfo_index = i;
       vi.value_index = i - num_static_constant_vars;
       vi.ppt = this;
    }
    var_infos = new_var_infos;
  }

  private void addVarInfos(Vector v) {
    int size = v.size();
    if (size == 0)
      return;
    int old_length = var_infos.length;
    VarInfo[] new_var_infos = new VarInfo[var_infos.length + v.size()];
    System.arraycopy(var_infos, 0, new_var_infos, 0, old_length);
    for (int i=0; i<size; i++) {
      VarInfo vi = (VarInfo) v.elementAt(i);
      new_var_infos[i+old_length] = vi;
      vi.varinfo_index = i+old_length;
      vi.value_index = i+old_length - num_static_constant_vars;
      vi.ppt = this;
    }
    var_infos = new_var_infos;
  }


  ///////////////////////////////////////////////////////////////////////////
  /// Finding an object or class ppt for a given ppt
  ///

  void set_controlling_ppts(PptMap all_ppts)
  {
    // TODO: also require that this is a public method
    if (ppt_name.isEnterPoint() || ppt_name.isExitPoint()) {
      PptTopLevel object_ppt = (PptTopLevel) all_ppts.get(ppt_name.makeObject());
      if (object_ppt != null) {
	controlling_ppts.add(object_ppt);
      } else {
	// If we didn't find :::OBJECT, fall back to :::CLASS
	PptTopLevel class_ppt = (PptTopLevel) all_ppts.get(ppt_name.makeClassStatic());
	if (class_ppt != null) {
	  controlling_ppts.add(class_ppt);
	}
      }
    } else if (ppt_name.isObjectInstanceSynthetic()) {
      PptTopLevel class_ppt = (PptTopLevel) all_ppts.get(ppt_name.makeClassStatic());
      if (class_ppt != null) {
	controlling_ppts.add(class_ppt);
      }
    }
  }


  ///////////////////////////////////////////////////////////////////////////
  /// Adding special variables
  ///

  // Given a program point, if it represents a function exit, then
  // return the corresponding function entry point.  The result is
  // cached in the entry_ppt slot, to prevent repeating this expensive
  // computation.

  void compute_entry_ppt(PptMap all_ppts) {
    if (ppt_name.isExitPoint()) {
      entry_ppt = (PptTopLevel) all_ppts.get(ppt_name.makeEnter());
      // System.out.println("Adding exit point " + this.name + " to " + entry_ppt.name);
      entry_ppt.exit_ppts.add(this);
    }
  }

  // Add "_orig" (prestate) variables to the program point.
  // Derivation should not yet have occurred for the entry program point.
  void add_orig_vars(PptTopLevel entry_ppt) {

    VarInfo[] begin_vis = entry_ppt.var_infos;
    num_orig_vars = begin_vis.length - entry_ppt.num_static_constant_vars;
    Assert.assert(num_orig_vars == entry_ppt.num_tracevars);
    // Don't bother to include the constants.
    VarInfo[] new_vis = new VarInfo[num_orig_vars];
    int new_vis_index = 0;
    for (int i=0; i<begin_vis.length; i++) {
      VarInfo vi = begin_vis[i];
      if (vi.isStaticConstant() || vi.isDerived())
	continue;
      VarInfo origvar = VarInfo.origVarInfo(vi);
      {
        VarInfo postvar = findVar(vi.name);
        origvar.comparability = postvar.comparability.makeAlias(origvar.name);
      }
      new_vis[new_vis_index] = origvar;
      new_vis_index++;
    }
    Assert.assert(new_vis_index == num_orig_vars);
    addVarInfos(new_vis);
  }



  /// Possibly just blow this off; I'm not sure I care about it.
  /// In any event, leave it until later.
  //
  // void add_invocation_count_vars() {
  //
  //   // Add invocation counts
  //   if (compute_invocation_counts) {
  //     for ppt in fns_to_process {
  //       these_var_infos = fn_var_infos[ppt];
  //       for callee in fn_invocations.keys() {
  // 	calls_var_name = "calls(%s)" % callee;
  // 	these_var_infos.append(var_info(calls_var_name, "integral", "always", len(these_var_infos)));
  // 	these_values.append(fn_invocations[callee]);
  // 	current_var_index++;
  //       }
  //     }
  //   }
  //
  //       (ppt_sans_suffix, ppt_suffix) = (string.split(ppt, ":::", 1) + [""])[0:2]
  //       if ((ppt_suffix != "EXIT")
  // 	  and (ppt_suffix[0:4] != "EXIT")):
  // 	  continue
  //       these_var_infos = fn_var_infos[ppt]
  //       entry_ppt = ppt_sans_suffix + ":::ENTER"
  //       for vi in fn_var_infos[entry_ppt][0:fn_truevars[entry_ppt]]:
  // 	  these_var_infos.append(var_info("orig(" + vi.name + ")", vi.type, comparability_make_alias(vi.name, vi.comparability), len(these_var_infos)))
  //
  // }


  ///////////////////////////////////////////////////////////////////////////
  /// Derived variables
  ///

  // Convenience function for PptConditional initializer (which can't
  // contain statements but can call a function).
  public VarInfo[] trace_and_orig_and_const_vars() {
    // Not ArraysMDE.subarray(var_infos, 0, num_tracevars + num_orig_vars)
    // because its result Object[] cannot be cast to VarInfo[].
    int total_vars = num_tracevars + num_orig_vars + num_static_constant_vars;
    VarInfo[] result = new VarInfo[total_vars];
    System.arraycopy(var_infos, 0, result, 0, total_vars);
    return result;
  }


  // This is here because I think it doesn't make sense to derive except
  // from a PptTopLevel (and possibly a PptConditional?).  Perhaps move it
  // later.

  public static boolean worthDerivingFrom(VarInfo vi) {
    // This prevents derivation from ever occurring on
    // derived variables.  Ought to put this under the
    // control of the individual Derivation objects.

    // System.out.println("worthDerivingFrom(" + vi.name + "): "
    //                    + "derivedDepth=" + vi.derivedDepth()
    //                    + ", isCanonical=" + vi.isCanonical()
    //                    + ", canBeMissing=" + vi.canBeMissing);
    return ((vi.derivedDepth() < 2)
            && (vi.isCanonical())
            && (!vi.canBeMissing));

    // Should add this (back) in:
	    // && !vi.always_missing()
	    // && !vi.always_equal_to_null();

    // Testing for being canonical is going to be a touch tricky when we
    // integrate derivation and inference, because when something becomes
    // non-canonical we'll have to go back and derive from it, etc.  It's
    // almost as if that is a new variable appearing.  But it did appear in
    // the list until it was found to be equal to another and removed from
    // the list!  I need to decide whether the time savings of not
    // processing the non-canonical variables are worth the time and
    // complexity of making variables non-canonical and possibly canonical
    // again.

  }


  final static int derivation_passes = 2;

  // To verify that these are all the factories of interest, do
  // cd ~/research/invariants/daikon/derive; search -i -n 'extends.*derivationfactory'

  transient UnaryDerivationFactory[][] unaryDerivations
    = new UnaryDerivationFactory[][] {
      // pass1
       { new SequenceLengthFactory() },
      // pass2
       { new SequenceInitialFactory(),
         new SequenceMinMaxSumFactory(), }
    };

  transient BinaryDerivationFactory[][] binaryDerivations
    = new BinaryDerivationFactory[][] {
      // pass1
      { },
      // pass2
      { new SequenceScalarSubscriptFactory(),
	new ScalarSequencesIntersectionFactory(),
	new StringSequencesIntersectionFactory(),
	new ScalarSequencesUnionFactory(),
	new StringSequencesUnionFactory(),
        new SequenceStringSubscriptFactory(), }
    };


  /**
   * This does no inference; it just calls deriveVariablesOnePass once per pass.
   * It returns a Vector of Derivation objects.<p>
   *
   * If derivation_index == (a, b, c) and n = len(var_infos), then
   * the body of this loop:
   * <li>
   *   <ul> does pass1 introduction for b..a
   *   <ul> does pass2 introduction for c..b
   * </li>
   * and afterward, derivation_index == (n, a, b).
   **/
  public Vector derive() {
    Assert.assert(ArraysMDE.sorted_descending(derivation_indices));

    Vector result = new Vector();
    for (int pass=1; pass<=derivation_passes; pass++) {
      int this_di = derivation_indices[pass];
      int last_di = derivation_indices[pass-1];
      if (Global.debugDerive.isDebugEnabled())
        Global.debugDerive.debug("pass=" + pass + ", range=" + this_di + ".." + last_di);
      if (this_di == last_di) {
        if (Global.debugDerive.isDebugEnabled()) {
          Global.debugDerive.debug("No pass " + pass + " derivation to do");
        }
	continue;
      }
      result.addAll(deriveVariablesOnePass(this_di, last_di,
					   unaryDerivations[pass-1],
					   binaryDerivations[pass-1]));
    }
    // shift values in derivation_indices:  convert [a,b,c] into [n,a,b]
    for (int i=derivation_passes; i>0; i--)
      derivation_indices[i] = derivation_indices[i-1];
    derivation_indices[0] = var_infos.length + result.size();

    if (Global.debugDerive.isDebugEnabled()) {
      Global.debugDerive.debug(name + ": derived " + result.size() + " new variables; "
                         + "new derivation_indices: "
                         + ArraysMDE.toString(derivation_indices));
      // Alternately, and probably more usefully
      for (int i=0; i<result.size(); i++) {
        System.out.println("  " + ((Derivation)result.elementAt(i)).getVarInfo().name);
      }
    }
    return result;
  }


  /**
   * This routine does one "pass"; that is, it adds some set of derived
   * variables, according to the functions that are passed in.  All the
   * results involve VarInfo objects at indices i such that vi_index_min <=
   * i < vi_index_limit (and possibly also involve other VarInfos).
   **/
  Vector deriveVariablesOnePass(int vi_index_min, int vi_index_limit, UnaryDerivationFactory[] unary, BinaryDerivationFactory[] binary) {

    if (Global.debugDerive.isDebugEnabled())
      Global.debugDerive.debug("deriveVariablesOnePass: vi_index_min=" + vi_index_min
                         + ", vi_index_limit=" + vi_index_limit
                         + ", unary.length=" + unary.length
                         + ", binary.length=" + binary.length);

    Vector result = new Vector();

    for (int i=vi_index_min; i<vi_index_limit; i++) {
      VarInfo vi = var_infos[i];
      if (!worthDerivingFrom(vi)) {
        if (Global.debugDerive.isDebugEnabled()) {
          Global.debugDerive.debug("Unary: not worth deriving from " + vi.name);
        }
	continue;
      }
      for (int di=0; di<unary.length; di++) {
	UnaryDerivationFactory d = unary[di];
        UnaryDerivation[] uderivs = d.instantiate(vi);
        if (uderivs != null) {
          for (int udi=0; udi<uderivs.length; udi++) {
            UnaryDerivation uderiv = uderivs[udi];
            if ((Daikon.var_omit_regexp != null)
                && Global.regexp_matcher.contains(uderiv.getVarInfo().name.name(), Daikon.var_omit_regexp)) {
              continue;
            }
            result.add(uderiv);
          }
        }
      }
    }

    // I want to get all pairs such that at least one of the elements is
    // under consideration, but I want to generate each such pair only
    // once.  This probably isn't the most efficient technique, but it's
    // probably adequate and is not excessively complicated or excessively
    // slow.
    for (int i1=0; i1<var_infos.length; i1++) {
      VarInfo vi1 = var_infos[i1];
      if (!worthDerivingFrom(vi1)) {
        if (Global.debugDerive.isDebugEnabled()) {
          Global.debugDerive.debug("Binary first VarInfo: not worth deriving from " + vi1.name);
        }
	continue;
      }
      boolean target1 = (i1 >= vi_index_min) && (i1 < vi_index_limit);
      int i2_min = (target1 ? i1+1 : Math.max(i1+1, vi_index_min));
      int i2_limit = (target1 ? var_infos.length : vi_index_limit);
      // if (Global.debugDerive.isDebugEnabled())
      //   Global.debugDerive.debug("i1=" + i1
      //                      + ", i2_min=" + i2_min
      //                      + ", i2_limit=" + i2_limit);
      for (int i2=i2_min; i2<i2_limit; i2++) {
	VarInfo vi2 = var_infos[i2];
	if (!worthDerivingFrom(vi2)) {
          if (Global.debugDerive.isDebugEnabled()) {
            Global.debugDerive.debug("Binary: not worth deriving from ("
                               + vi1.name + "," + vi2.name + ")");
          }
          continue;
        }
	// if ((!target1) && (ArraysMDE.indexOfEq(var_infos, vi2) == -1))
	//   // Do nothing if neither of these variables is under consideration.
	//   continue;
	for (int di=0; di<binary.length; di++) {
	  BinaryDerivationFactory d = binary[di];
          BinaryDerivation[] bderivs = d.instantiate(vi1, vi2);
          if (bderivs != null) {
            for (int bdi=0; bdi<bderivs.length; bdi++) {
              BinaryDerivation bderiv = bderivs[bdi];
              if ((Daikon.var_omit_regexp != null)
                  && Global.regexp_matcher.contains(bderiv.getVarInfo().name.name(), Daikon.var_omit_regexp)) {
                continue;
              }
              result.add(bderiv);
            }
          }
	}
      }
    }

    for (int i=0; i<result.size(); i++) {
      Assert.assert(result.elementAt(i) instanceof Derivation
                    // , "Non-Derivation " + result.elementAt(i) + " at index " + i
                    );
    }

    return result;
  }


  ///
  /// Adding derived variables
  ///

  // This doesn't compute what the derived variables should be, just adds
  // them after being computed.

  // derivs is a Vector of Derivation objects
  void addDerivedVariables(Vector derivs) {
    Derivation[] derivs_array
      = (Derivation[]) derivs.toArray(new Derivation[0]);
    addDerivedVariables(derivs_array);
  }

  void addDerivedVariables(Derivation[] derivs) {

    VarInfo[] vis = new VarInfo[derivs.length];
    for (int i=0; i<derivs.length; i++) {
      vis[i] = derivs[i].getVarInfo();
    }
    addVarInfos(vis);

    // Since I am only modifying members, not making new objects, and since
    // I am using an Eq hash table, I don't need to rehash.
    values.extend(derivs);
  }




  ///////////////////////////////////////////////////////////////////////////
  /// Manipulating values
  ///

  void add(ValueTuple vt, int count) {
    // System.out.println("PptTopLevel " + name + ": add " + vt);
    Assert.assert(vt.size() == var_infos.length - num_static_constant_vars);

    values.add(vt, count);

    // Add to all the views
    // for (Iterator itor = views.keySet().iterator() ; itor.hasNext() ; ) {
    for (Iterator itor = views.iterator() ; itor.hasNext() ; ) {
      PptSlice view = (PptSlice) itor.next();
      view.add(vt, count);
      if (view.invs.size() == 0)
        itor.remove();
    }

  }



  ///////////////////////////////////////////////////////////////////////////
  /// Initial processing
  ///

  // This function is called to jump-start processing; it creates all the
  // views (and thus invariants) and derived variables.  Afterward, we just
  // check those invariants.  (That might require us to add more derived
  // variables and invariants, though -- for instance, if an invariant that
  // had suppressed a derived variable becomes falsified.  An equality
  // invariant is an example of such an invariant.)

  // As of 10/25/99, we don't do the second part:  just read the entire
  // data file before doing any processing.

  // The way this works is:
  //  * do derivation by stages
  //  * all inference must be performed over a variable before it may be
  //    derived from.  This implies that as soon as we derive a variable,
  //    we should do all inference over it.
  //  * inference is done by stages, too:  first equality invariants,
  //    then others.  See instantiate_views.


  public void initial_processing() {
    if (debug.isDebugEnabled())
      debug.debug("initial_processing for " + name);

    // I probably can't do anything about it if this is called
    // subsequently; but I should be putting off initial_processing for
    // each program point until it has many samples anyway.
    if (!has_samples())
      return;

    derivation_indices = new int[derivation_passes+1];
    // Extraneous, since Java initializes the array to all zeros.
    // Arrays.fill(derivation_passes, 0);
    // Not num_tracevars because we also care about _orig, etc.
    instantiate_views(0, var_infos.length);

    // Eventually, integrate derivation and inference.  That will require
    // incrementally adding new variables, slices, and invariants.  For
    // now, don't bother:  I want to just get something working first.
    while (derivation_indices[derivation_passes] < var_infos.length) {
      Vector derivations = derive();

      // Using "addVarInfos(derivations)" would do only part of the work.
      addDerivedVariables(derivations);

      Assert.assert(derivation_indices[0] == var_infos.length);
      instantiate_views(var_infos.length - derivations.size(), var_infos.length);
    }
    if (debug.isDebugEnabled())
      debug.debug("Done with initial_processing, " + var_infos.length
		  + " variables");
    Assert.assert(derivation_indices[derivation_passes] == var_infos.length);
  }


  ///////////////////////////////////////////////////////////////////////////
  /// Creating invariants
  ///

  // Is a Vector if we are adding views; this obviates the need for a
  // "boolean adding_views" variable.
  private Vector views_to_remove_deferred = null;
  // This is to avoid making a new vector every time through the loop;
  // just reuse this one.  (This probably isn't such a big deal.)
  private Vector vtrd_cache = new Vector(2);

  // I can't decide which loop it's more efficient to make the inner loop:
  // the loop over samples or the loop over slices.

  // slices_vector is a Vector of PptSlice; this routine does not modify it.
  // Maybe this should return the rejected views.
  public void addViews(Vector slices_vector) {
    if (slices_vector.isEmpty())
      return;

    // Don't modify the actual parameter
    slices_vector = (Vector) slices_vector.clone();

    // This might be a brand-new Slice, and instantiate_invariants for this
    // pass might not have come up with any invariants.
    for (Iterator itor = slices_vector.iterator(); itor.hasNext(); ) {
      PptSlice slice = (PptSlice) itor.next();
      if (slice.invs.size() == 0)
        // removes the element from slices_vector
        itor.remove();
    }

    // use an array because iterating over it will be more efficient, I suspect.
    PptSlice[] slices;
    int num_slices;

    // This is also duplicated below.
    views_to_remove_deferred = vtrd_cache;
    slices = (PptSlice[]) slices_vector.toArray(new PptSlice[0]);
    num_slices = slices.length;

    // System.out.println("Adding views for " + name);
    // for (int i=0; i<slices.length; i++) {
    //   System.out.println("  View: " + slices[i].name);
    // }
    // values.dump();

    // System.out.println("Number of samples for " + name + ": "
    //                    + values.num_samples()
    //                    + ", number of values: " + values.num_values());
    // If I recorded mod bits in value.ValueSet(), I could use it here instead.
    for (Iterator vt_itor = values.sampleIterator(); vt_itor.hasNext(); ) {
      VarValuesOrdered.ValueTupleCount entry = (VarValuesOrdered.ValueTupleCount) vt_itor.next();
      ValueTuple vt = entry.value_tuple;
      int count = entry.count;
      for (int i=0; i<num_slices; i++) {
        // System.out.println("" + slices[i] + " .add(" + vt + ", " + count + ")");
        slices[i].add(vt, count);
      }
      if (views_to_remove_deferred.size() > 0) {
        // Inefficient, but easy to code.
        Assert.assert(slices_vector.containsAll(views_to_remove_deferred));
        slices_vector.removeAll(views_to_remove_deferred);
        views_to_remove_deferred.clear();
        if (slices_vector.size() == 0)
          break;
        slices = (PptSlice[]) slices_vector.toArray(new PptSlice[0]);
        num_slices = slices.length;
      }
    }

    views.addAll(slices_vector);
    for (int i=0; i<num_slices; i++) {
      slices[i].already_seen_all = true;
      // System.out.println("Now " + slices[i].name + " has seen it all");
    }

    views_to_remove_deferred = null;
  }

  // Old version with the other loop outermost
  // private boolean adding_views;
  // private Ppt view_to_remove_deferred = null;
  // // Vector of PptSlice.
  // // Maybe this should return the rejected views.
  // void addViews(Vector slices) {
  //   adding_views = true;
  //   // Since there are more valuetuples than program points, and program
  //   // points are likely to disappear midway through processing, put the
  //   // program point loop outermost.  (Does this make sense?  I'm not sure.)
  //   for (Iterator slice_itor = slices.iterator() ; slice_itor.hasNext() ; ) {
  //     Ppt this_slice = (Ppt) slice_itor.next();
  //     Assert.assert(view_to_remove_deferred == null);
  //     for (Iterator vt_itor = values.entrySet().iterator() ;
  //          ((view_to_remove_deferred == null) && vt_itor.hasNext()) ;
  //          /* no increment in for loop */ ) {
  //       Map.Entry entry = (Map.Entry) vt_itor.next();
  //       ValueTuple vt = (ValueTuple) entry.getKey();
  //       int count = ((Integer) entry.getValue()).intValue();
  //       this_slice.add(vt, count);
  //     }
  //     if (view_to_remove_deferred != null) {
  //       Assert.assert(view_to_remove_deferred == this_slice);
  //       // removes this_slice from slice_itor
  //       slice_itor.remove();
  //     }
  //   }
  //   views.addAll(slices);
  //   adding_views = false;
  // }


  public void removeView(Ppt slice) {
    // System.out.println("removeView " + slice.name + " " + slice);
    if (views_to_remove_deferred != null) {
      views_to_remove_deferred.add(slice);
    } else {
      boolean removed = views.remove(slice);
      Assert.assert(removed);
    }
  }


  // A slice is a specific kind of view, but we don't call this
  // findView because it doesn't find an arbitrary view.
  public PptSlice1 findSlice(VarInfo v) {
    for (Iterator itor = views.iterator() ; itor.hasNext() ; ) {
      PptSlice view = (PptSlice) itor.next();
      if ((view.arity == 1) && (v == view.var_infos[0]))
        return (PptSlice1) view;
    }
    return null;
  }

  public PptSlice2 findSlice(VarInfo v1, VarInfo v2) {
    Assert.assert(v1.varinfo_index < v2.varinfo_index);
    for (Iterator itor = views.iterator() ; itor.hasNext() ; ) {
      PptSlice view = (PptSlice) itor.next();
      if ((view.arity == 2)
          && (v1 == view.var_infos[0])
          && (v2 == view.var_infos[1]))
        return (PptSlice2) view;
    }
    return null;
  }

  public PptSlice2 findSlice_unordered(VarInfo v1, VarInfo v2) {
    if (v1.varinfo_index < v2.varinfo_index) {
      return findSlice(v1, v2);
    } else {
      return findSlice(v2, v1);
    }
  }

  public PptSlice3 findSlice(VarInfo v1, VarInfo v2, VarInfo v3) {
    Assert.assert(v1.varinfo_index < v2.varinfo_index);
    Assert.assert(v2.varinfo_index < v3.varinfo_index);
    for (Iterator itor = views.iterator() ; itor.hasNext() ; ) {
      PptSlice view = (PptSlice) itor.next();
      if ((view.arity == 3)
          && (v1 == view.var_infos[0])
          && (v2 == view.var_infos[1])
          && (v3 == view.var_infos[2]))
        return (PptSlice3) view;
    }
    return null;
  }

  public int indexOf(String varname) {
    for (int i=0; i<var_infos.length; i++) {
      if (var_infos[i].name.name().equals(varname)) {
        return i;
      }
    }
    return -1;
  }


  // At present, this needs to occur after deriving variables, because
  // I haven't integrated derivation and inference yet.
  // (This function doesn't exactly belong in this part of the file.)

  // Should return a list of the views created, perhaps.


  /**
   * Install views (and thus invariants).
   * This function does cause all invariants over the new views to be computed.
   * The installed views and invariants will all have at least one element with
   * index i such that vi_index_min <= i < vi_index_limit.
   * (However, we also assume that vi_index_limit == var_infos.length.)
   **/
  void instantiate_views(int vi_index_min, int vi_index_limit) {
    if (Global.debugInfer.isDebugEnabled())
      Global.debugInfer.debug("instantiate_views: " + this.name
			   + ", vi_index_min=" + vi_index_min
			   + ", vi_index_limit=" + vi_index_limit
			   + ", var_infos.length=" + var_infos.length);

    // It might pay to instantiate views for variables one at a time, to
    // save work.  I'm not sure, but it does seem plausible.
    // This test prevents that for now.
    Assert.assert(var_infos.length == vi_index_limit);


    // Stage invariant detection:
    //  1. unary constant (skip if canBeMissing)
    //  2. binary equal
    //     (skip if canBeMissing;
    //     only if types are the same/compatible;
    //     can do this sequentially rather than all at once)
    //      * set canonicalness
    //  3. all other unary
    //     (skip if non-canonical, constant, canBeMissing)
    //  4. all other binary
    //     (skip if non-canonical or missing; sometimes skip if constant)
    //  5. ternary
    // A very slightly tricky thing is not duplicating an already-existing
    // view during the "all other" phases.

    if (vi_index_min == vi_index_limit)
      return;

//     if var_values == {}:
//         # This function was never executed
//         return

    // used only for debugging
    int old_num_vars = var_infos.length;
    int old_num_views = views.size();

    /// 1. unary constant

    // Unary slices/invariants.
    Vector unary_views = new Vector(vi_index_limit-vi_index_min);
    for (int i=vi_index_min; i<vi_index_limit; i++) {
      // System.out.println("Perhaps add unary view for " + var_infos[i].name);
      if (var_infos[i].canBeMissingCheck()) {
        if (Global.debugDerive.isDebugEnabled()) {
          Global.debugDerive.debug("In binary equality, " + var_infos[i].name + " can be missing");
        }
        continue;
      }
      // I haven't computed any invariants over it yet -- how am I to know
      // whether it's canonical??
      // if (!var_infos[i].isCanonical())
      //   continue;
      PptSlice1 slice1 = new PptSlice1(this, var_infos[i]);
      // first pass of unary invariant instantiation
      slice1.instantiate_invariants(1);
      unary_views.add(slice1);
      // System.out.println("Definitely add unary view for " + var_infos[i].name);
    }
    addViews(unary_views);
    set_dynamic_constant_slots(unary_views);

    // Now some elements of unary_views are installed, but others are not
    // and are incomplete, because we stopped adding new values as soon as
    // they were found not to hold.  We discard them later, but for now we
    // want to remember all the ones we tried.


    /// 2. binary equality

    // Binary slices/invariants.
    Vector binary_views = new Vector();
    for (int i1=0; i1<vi_index_limit; i1++) {
      // Don't call canBeMissingCheck(); check directly,
      // lest equality be non-transitive
      if (var_infos[i1].canBeMissing) {
        if (Global.debugDerive.isDebugEnabled()) {
          Global.debugDerive.debug("In binary equality, " + var_infos[i1].name + " can be missing");
        }
        continue;
      }
      // I can check canonicalness only if we've already computed
      // invariants over it.
      if ((i1 < vi_index_min) && (!var_infos[i1].isCanonical())) {
        if (Global.debugDerive.isDebugEnabled()) {
          Global.debugDerive.debug("Skipping non-canonical non-target variable1 "
                             + var_infos[i1].name);
        }
        continue;
      }
      boolean target1 = (i1 >= vi_index_min) && (i1 < vi_index_limit);
      int i2_min = (target1 ? i1+1 : Math.max(i1+1, vi_index_min));
      if (Global.debugInfer.isDebugEnabled())
        Global.debugInfer.debug("instantiate_views"
			     + "(" + vi_index_min + "," + vi_index_limit + ")"
			     + " i1=" + i1 + " (" + var_infos[i1].name + ")"
			     + ", i2_min=" + i2_min
			     );
      for (int i2=i2_min; i2<vi_index_limit; i2++) {
        // System.out.println("Trying binary instantiate_views"
        //                    + " i1=" + i1 + " (" + var_infos[i1].name + "),"
        //                    + " i2=" + i2 + " (" + var_infos[i2].name + ")");
        if (var_infos[i2].canBeMissingCheck()) {
          if (Global.debugDerive.isDebugEnabled()) {
            Global.debugDerive.debug("In binary equality vs. " + var_infos[i1].name
                               + ", " + var_infos[i2].name + " can be missing");
          }
          continue;
        }
        // I can check canonicalness only if we've already computed
        // invariants over it.
        if ((i2 < vi_index_min) && (!var_infos[i2].isCanonical())) {
          if (Global.debugDerive.isDebugEnabled()) {
            Global.debugDerive.debug("Skipping non-canonical non-target variable2 "
                               + var_infos[i2].name);
          }
          continue;
        }
        // We'll take care of this elsewhere, in part because putting
        // everything in binary_views simplifies things later on.
        // // For equality invariants (only), skip if differing types.
        // if (var_infos[i1].rep_type != var_infos[i2].rep_type) {
        //   continue;
        // }

        PptSlice slice2 = new PptSlice2(this, var_infos[i1], var_infos[i2]);
        // System.out.println("Created PptSlice2 " + slice2.name);
        slice2.instantiate_invariants(1);
        binary_views.add(slice2);
      }
    }
    addViews(binary_views);
    set_equal_to_slots(binary_views, vi_index_min, vi_index_limit);

    // 3. all other unary invariants
    if (debug.isDebugEnabled())
      debug.debug(unary_views.size() + " unary views for pass 2 instantiate_invariants " + name);
    Vector unary_views_pass2 = new Vector(unary_views.size());
    for (int i=0; i<unary_views.size(); i++) {
      PptSlice1 unary_view = (PptSlice1) unary_views.elementAt(i);
      Assert.assert(unary_view.arity == 1);
      VarInfo var = unary_view.var_infos[0];
      if (!var.isCanonical()) {
        if (debug.isDebugEnabled())
          debug.debug("Skipping pass 2 unary instantiate_invariants: "
                             + var.name + " is not canonical");
        continue;
      }
      if (unary_view.var_info.isConstant()) {
        if (debug.isDebugEnabled())
          debug.debug("Skipping pass 2 unary instantiate_invariants: "
                             + var.name + " is constant=" + unary_view.var_info.constantValue());
        continue;
      }
      if (views.contains(unary_view)) {
        // There is only one type of unary invariant in pass 1:
        // OneOf{scalar,Sequence}.  It must have been successful, or this
        // view wouldn't have been installed.
        // Assert.assert(unary_view.invs.size() == 1);

        for (int j=0; j<unary_view.invs.size(); j++) {
          Invariant inv = (Invariant) unary_view.invs.elementAt(j);
          Assert.assert(inv instanceof OneOf);
        }
      } else {
        // The old one was a failure (and so saw only a subset of all the
        // values); recreate it.
        unary_view = new PptSlice1(this, var);
        unary_views_pass2.add(unary_view);
      }
      unary_view.instantiate_invariants(2);
    }
    addViews(unary_views_pass2);
    // Save some space
    for (int i=0; i<unary_views.size(); i++) {
      ((PptSlice) unary_views.elementAt(i)).clear_cache();
    }
    for (int i=0; i<unary_views_pass2.size(); i++) {
      ((PptSlice) unary_views_pass2.elementAt(i)).clear_cache();
    }
    unary_views = null;
    unary_views_pass2 = null;

    // 4. all other binary invariants
    Vector binary_views_pass2 = new Vector(binary_views.size());
    for (int i=0; i<binary_views.size(); i++) {
      PptSlice2 binary_view = (PptSlice2) binary_views.elementAt(i);
      Assert.assert(binary_view.arity == 2);
      VarInfo var1 = binary_view.var_infos[0];
      VarInfo var2 = binary_view.var_infos[1];
      if (!var1.isCanonical())
        continue;
      if (!var2.isCanonical())
        continue;
      if (var1.isConstant())
        continue;
      if (var2.isConstant())
        continue;
      if (views.contains(binary_view)) {
        // There is only one type of binary invariant in pass 1:
        // {Int,Seq,String}Comparison.  It must have been successful, or this view
        // wouldn't have been installed.
        Assert.assert(binary_view.invs.size() == 1);
        Invariant inv = (Invariant) binary_view.invs.elementAt(0);
        Assert.assert(inv instanceof Comparison);
      } else {
        // The old one was a failure (and so saw only a subset of all the
        // values); recreate it.
        binary_view = new PptSlice2(this, var1, var2);
        binary_views_pass2.add(binary_view);
      }
      binary_view.instantiate_invariants(2);
    }
    addViews(binary_views_pass2);
    // Compute exact_nonunary_invariants, then save some space
    set_exact_nonunary_invariants_slots(binary_views);
    set_exact_nonunary_invariants_slots(binary_views_pass2);
    binary_views = null;
    binary_views_pass2 = null;

    // 5. ternary invariants
    if (! Daikon.disable_ternary_invariants) {
      Vector ternary_views = new Vector();
      for (int i1=0; i1<vi_index_limit; i1++) {
        VarInfo var1 = var_infos[i1];
        if (var1.canBeMissingCheck()) {
          if (Global.debugDerive.isDebugEnabled()) {
            Global.debugDerive.debug("In ternary, " + var1.name + " can be missing");
          }
          continue;
        }
        if (!var1.isCanonical())
          continue;
        if (var1.isConstant())
          continue;

        boolean target1 = (i1 >= vi_index_min) && (i1 < vi_index_limit);
        for (int i2=i1+1; i2<vi_index_limit; i2++) {
          VarInfo var2 = var_infos[i2];
          if (var2.canBeMissingCheck()) {
            if (Global.debugDerive.isDebugEnabled()) {
              Global.debugDerive.debug("In ternary vs. " + var1.name
                                 + ", " + var2.name + " can be missing");
            }
            continue;
          }
          if (!var2.isCanonical())
            continue;
          if (var2.isConstant())
            continue;
          if (var1.hasExactInvariant(var2)) {
            // This number isn't quite right:  it depends on what the type
            // of var3 will be.  But leave it as a conservative
            // approximation (because we would save this many for many
            // different var3).
            Global.subexact_noninstantiated_invariants
              += ThreeScalarFactory.max_instantiate;
            continue;
          }

          boolean target2 = (i2 >= vi_index_min) && (i2 < vi_index_limit);
          int i3_min = ((target1 || target2) ? i2+1 : Math.max(i2+1, vi_index_min));
          if (Global.debugInfer.isDebugEnabled())
	    Global.debugInfer.debug("instantiate_views"
				 + "(" + vi_index_min + "," + vi_index_limit + ")"
				 + " i1=" + i1
				 + " i2=" + i2
				 + ", i3_min=" + i3_min
				 );
          for (int i3=i3_min; i3<vi_index_limit; i3++) {
            Assert.assert(((i1 >= vi_index_min) && (i1 < vi_index_limit))
                          || ((i2 >= vi_index_min) && (i2 < vi_index_limit))
                          || ((i3 >= vi_index_min) && (i3 < vi_index_limit)));
            Assert.assert((i1 < i2) && (i2 < i3));
            VarInfo var3 = var_infos[i3];
            if (var3.canBeMissingCheck()) {
              if (Global.debugDerive.isDebugEnabled()) {
                Global.debugDerive.debug("In ternary vs. ("
                                   + var1.name + "," + var2.name + ")"
                                   + ", " + var2.name + " can be missing");
              }
              continue;
            }
            if (!var3.isCanonical())
              continue;
            if (var3.isConstant())
              continue;
            if (var1.hasExactInvariant(var3)
                || var2.hasExactInvariant(var3)) {
              // No invariants if any of the types is array.
              if (var1.rep_type.isArray()
                || var2.rep_type.isArray()
                || var3.rep_type.isArray())
                continue;
              Global.subexact_noninstantiated_invariants
                += ThreeScalarFactory.max_instantiate;
              continue;
            }

            // (For efficiency, I could move this earlier.  But that's not
            // completely fair, so I won't for now.)
            // For now, only ternary invariants not involving any arrays
            if (var1.rep_type.isArray()
                || var2.rep_type.isArray()
                || var3.rep_type.isArray())
              continue;

            PptSlice3 slice3 = new PptSlice3(this, var1, var2, var3);
            slice3.instantiate_invariants(1);
            slice3.instantiate_invariants(2);
            ternary_views.add(slice3);
          }
        }
      }
      addViews(ternary_views);
      set_exact_nonunary_invariants_slots(ternary_views);
    }


    if (debug.isDebugEnabled())
      debug.debug(views.size() - old_num_views + " new views for " + name);

    // This method didn't add any new variables.
    Assert.assert(old_num_vars == var_infos.length);

    // now unary_views, binary_views, and ternary_views get garbage-collected.
  }

  // Set the dynamic_constant slots of all the new variables.
  void set_dynamic_constant_slots(Vector unary_views) {
    for (int i=0; i<unary_views.size(); i++) {
      PptSlice1 unary_view = (PptSlice1) unary_views.elementAt(i);
      // System.out.println("set_dynamic_constant_slots " + unary_view.name + " " + views.contains(unary_view));
      Assert.assert(unary_view.arity == 1);
      // If this view has been installed in the views slot (ie, it has not
      // been eliminated already).
      if (views.contains(unary_view)) {
        // This is not true any longer.
        // // There is only one type of unary invariant in pass 1:
        // // OneOf{Scalar,Sequence}.  It must have been successful, or this
        // // view wouldn't have been installed.
        // Assert.assert(unary_view.invs.size() == 1);
        // Invariant inv = (Invariant) unary_view.invs.elementAt(0);

        for (int j=0; j<unary_view.invs.size(); j++) {
          Invariant inv = (Invariant) unary_view.invs.elementAt(j);
          inv.finished = true;
          // unary_view.already_seen_all = true;
          OneOf one_of = (OneOf) inv;
          // System.out.println("num_elts: " + one_of.num_elts());
          if ((one_of.num_elts() == 1)
              && (! (inv instanceof EltOneOf))
              && (! (inv instanceof EltOneOfString))) {
            // System.out.println("Constant " + inv.ppt.name + " " + one_of.var().name + " because of " + inv.format() + "    " + inv.repr_prob() + "    " + inv.justified());
            // Should be Long, not Integer.
            Assert.assert(! (one_of.elt() instanceof Integer));
            one_of.var().dynamic_constant = one_of.elt();
            one_of.var().is_dynamic_constant = true;
            // System.out.println("set dynamic_constant to " + one_of.elt());
          }
        }
      } else {
        unary_view.clear_cache();
      }
    }
  }

  // Set the equal_to slots of all the new variables.
  void set_equal_to_slots(Vector binary_views, int vi_index_min, int vi_index_limit) {
    for (int i=0; i<binary_views.size(); i++) {
      PptSlice2 binary_view = (PptSlice2) binary_views.elementAt(i);
      Assert.assert(binary_view.arity == 2);

      if (binary_view.debugged) {
        System.out.println("Binary view " + binary_view.name + " has "
                           + (views.contains(binary_view) ? "not " : "") + "been eliminated.");
      }
      // If binary_view has been installed (hasn't yet been eliminated)
      if (views.contains(binary_view)) {

        // set_equal_to_slots runs after pass 1 of invariant introduction.
        // There is only one type of binary invariant in pass 1:
        // {Int,Seq,String}Comparison.  It must have been successful, or
        // this view wouldn't have been installed.
        Assert.assert(binary_view.invs.size() == 1);

        Invariant inv = (Invariant) binary_view.invs.elementAt(0);
        inv.finished = true;
        // binary_view.already_seen_all = true;
        Assert.assert(inv instanceof Comparison);
        // Not "inv.format" because that is null if not justified.
        // System.out.println("Is " + (IsEqualityComparison.it.accept(inv) ? "" : "not ")
        //                    + "equality: " + inv.repr());
        if (IsEqualityComparison.it.accept(inv)
            && inv.enoughSamples()) {
          VarInfo var1 = binary_view.var_infos[0];
          VarInfo var2 = binary_view.var_infos[1];
          Assert.assert(var1.varinfo_index < var2.varinfo_index);
          // System.out.println("found equality: " + var1.name + " = " + var2.name);
          // System.out.println("var1.equal_to="
          //                    + ((var1.equal_to == null) ? "null" : var1.equal_to.name)
          //                    + ", var2.equal_to="
          //                    + ((var2.equal_to == null) ? "null" : var2.equal_to.name));
          if ((var1.equal_to == null) && (var2.equal_to != null)) {
            var1.equal_to = var2.equal_to;
            if (debugEqualTo.isDebugEnabled()) {
              debugEqualTo.debug("Setting " + var1.name + ".equal_to = " + var1.equal_to.name);
            }
          } else if ((var1.equal_to != null) && (var2.equal_to == null)) {
            var2.equal_to = var1.equal_to;
            if (debugEqualTo.isDebugEnabled()) {
              debugEqualTo.debug("Setting " + var2.name + ".equal_to = " + var2.equal_to.name);
            }
          } else if ((var1.equal_to == null) && (var2.equal_to == null)) {
            // Can this cause the canonical version to not be the lowest-
            // numbered version?  I don't think so, because of the ordering
            // in which we are examining pairs.
            var1.equal_to = var1;
            var2.equal_to = var1;
            // System.out.println("Make " + var1.name + " canonical over " + var2.name + " at " + name);
          } else {
            // This is implied by the if-then sequence.
            // Assert.assert((var1.equal_to != null) && (var2.equal_to != null));
            if (var1.compatible(var2)
                && (var1.equal_to != var2.equal_to)) {
              // Used to be an assert
              System.out.println("Variables not equal: " + var1.name + " (= " + var1.equal_to.name + "), " + var2.name + " (= " + var2.equal_to.name + ") [indices " + var1.varinfo_index + ", " + var1.equal_to.varinfo_index + ", " + var2.varinfo_index + ", " + var2.equal_to.varinfo_index + "] at " + name);
            }
            Assert.assert(var1.equal_to.varinfo_index <= var1.varinfo_index);
            Assert.assert(var2.equal_to.varinfo_index <= var2.varinfo_index);
          }
        }
      } else {
        binary_view.clear_cache();
      }
    }
    for (int i=vi_index_min; i<vi_index_limit; i++) {
      VarInfo vi = var_infos[i];
      if (vi.equal_to == null) {
        if (debugEqualTo.isDebugEnabled()) {
          debugEqualTo.debug("Lonesome canonical var " + vi.varinfo_index + ": " + vi.name);
        }
        vi.equal_to = vi;
      }
    }
  }

  // Compute exact_nonunary_invariants
  void set_exact_nonunary_invariants_slots(Vector nonunary_views) {
    for (int j=0; j<nonunary_views.size(); j++) {
      PptSlice nonunary_view = (PptSlice) nonunary_views.elementAt(j);
      for (int k=0; k<nonunary_view.invs.size(); k++) {
        Invariant inv = (Invariant) nonunary_view.invs.elementAt(k);
        if (inv.isExact() && inv.enoughSamples()) {
          nonunary_view.var_infos[0].exact_nonunary_invariants.add(inv);
        }
      }
      nonunary_view.clear_cache();
    }
  }


  ///////////////////////////////////////////////////////////////////////////
  /// Creating conditioned views
  ///

  // This apparently can't appear in PptConditional, lest it never get called.
  // I guess PptConditional isn't instantiated unless it needs to be, but
  // it doesn't need to be unless GriesLisp has been instantiated already.

  static {
    // Would it be enough to say "GriesLisp dummy = null;"?  I'm not sure.
    // This does work, though.

    if (! Daikon.disable_splitting) {
       //new MiscSplitters();

      SplitterList.put(".*", new Splitter[] {
	new ReturnTrueSplitter(),
      });

      //new GriesLisp();
      //new WeissDsaaMDE();
      // These are outdated; they look for "field" instead of "this.field".
      // new SplitterList4Dsaa();
    }
  }


  public void addConditions(Splitter[] splits) {
    int len = splits.length;
    if ((splits == null) || (len == 0)) {
      if (Global.debugSplit.isDebugEnabled())
        Global.debugSplit.debug("No splits for " + name);
      return;
    }

    Vector pconds_vector = new Vector(2 * len);
    for (int i=0; i<len; i++) {
      PptConditional cond1 = new PptConditional(this, splits[i], false);
      if (! cond1.splitter_valid()) {
	  if (Global.debugSplit.isDebugEnabled())
	    Global.debugSplit.debug("Splitter not valid: " + cond1.name);
        continue;
      }
      pconds_vector.add(cond1);
      PptConditional cond2 = new PptConditional(this, splits[i], true);
      Assert.assert(cond2.splitter_valid());
      pconds_vector.add(cond2);
    }
    PptConditional[] pconds
      = (PptConditional[]) pconds_vector.toArray(new PptConditional[0]);
    int num_pconds = pconds.length;
    Assert.assert(num_pconds % 2 == 0);
    int num_splits = num_pconds/2;

    for (int i=0; i<num_pconds; i+=2) {
      Assert.assert(! pconds[i].splitter_inverse);
      Assert.assert(pconds[i+1].splitter_inverse);
      Assert.assert(pconds[i+1].splitter.condition().equals(pconds[i].splitter.condition()));
    }

    int trimlength = num_tracevars + num_orig_vars;

    int[][] cumulative_modbits = new int[num_pconds][trimlength];
    for (int i=0; i<num_pconds; i++) {
      Arrays.fill(cumulative_modbits[i], 1);
    }

    // Fill the new PptConditionals with values.
    for (Iterator vt_itor = values.sampleIterator(); vt_itor.hasNext(); ) {
      VarValuesOrdered.ValueTupleCount entry = (VarValuesOrdered.ValueTupleCount) vt_itor.next();
      ValueTuple vt = entry.value_tuple;
      int count = entry.count;
      // I do not want to use the same ValueTuple every time through the pconds
      // loop because the inserted ValueTuple will be modified in place.
      // It's OK to reuse its elements, though.
      ValueTuple vt_trimmed = vt.trim(trimlength);
      int[] trimmed_mods = vt_trimmed.mods;
      Object[] trimmed_vals = vt_trimmed.vals;
      for (int i=0; i<num_pconds; i+=2) {
        // I really only have to do one of these (depending on which way
        // the split goes), unless the splitter throws an error, in which
        // case I need to have done both.  Thus, do both, to be on the safe
        // side.
        ValueTuple.orModsInto(cumulative_modbits[i], trimmed_mods);
        ValueTuple.orModsInto(cumulative_modbits[i+1], trimmed_mods);
        boolean splitter_test;
        boolean split_exception = false;
        // System.out.println("Testing " + pconds[i].name);
        // This try block is tight so it doesn't accidentally catch
        // other errors.
        try {
          splitter_test = pconds[i].splitter.test(vt);
        } catch (Exception e) {
          // e.printStackTrace();
          // If an exception is thrown, don't put the data on either side
          // of the split.
          split_exception = true;
          splitter_test = false; // to pacify the Java compiler
        }
        if (! split_exception) {
          // System.out.println("Result = " + splitter_test);
          int index = (splitter_test ? i : i+1);
          // Do not reuse cum_mods!  It might itself be the
          // canonical version (returned by Intern.intern), and then
          // modifications would be bad.  Instead, create a new array.
          int[] cum_mods = cumulative_modbits[index];
          int[] new_mods = (int[]) trimmed_mods.clone();
          // This is somewhat like orModsInto, but not exactly.
          for (int mi=0; mi<trimlength; mi++) {
            if ((cum_mods[mi] == ValueTuple.MODIFIED)
                && (new_mods[mi] != ValueTuple.MISSING)) {
              new_mods[mi] = ValueTuple.MODIFIED;
              cum_mods[mi] = ValueTuple.UNMODIFIED;
            }
          }
          // System.out.println("Adding (count " + count + ") to " + pconds[index].name);
          pconds[index].add_nocheck(ValueTuple.makeFromInterned(trimmed_vals,
                                                                Intern.intern(new_mods)),
                                    count);
          // I don't want to do "Arrays.fill(cum_mods, 0)" because where
          // the value was missing, we didn't use up the modification bit.
          // I've already fixed it up above, anyway.
        }
      }
    }


    // Install the new conditional ppts, if they are not trivial.
    int parent_num_samples = num_samples();
    for (int i=0; i<num_pconds; i++) {
      // Don't bother with this conditioned view if it contains all or no samples.
      int this_num_samples = pconds[i].num_samples();
      if ((this_num_samples > 0) && (this_num_samples < parent_num_samples)) {
        views_cond.add(pconds[i]);
      } else {
        if (Global.debugSplit.isDebugEnabled())
          Global.debugSplit.debug("Omitting " + pconds[i].name + ": "
                             + this_num_samples + "/" + parent_num_samples
                             + " samples");
        // // Unconditional output, because it's too confusing otherwise.
        // if (this_num_samples == parent_num_samples) {
        //   System.out.println("Condition always satisfied: "
        //                      + pconds[i].name + " == " + this.name);
        // }
      }
    }
    if (Global.debugSplit.isDebugEnabled()) {
      Global.debugSplit.debug("" + views_cond.size() + " views on " + this.name);
      for (int i=0; i<views_cond.size(); i++) {
        PptConditional pcond = (PptConditional) views_cond.elementAt(i);
        System.out.println("    " + pcond.name);
      }
    }
    for (int i=0; i<views_cond.size(); i++) {
      PptConditional pcond = (PptConditional) views_cond.elementAt(i);
      pcond.initial_processing();
    }

  }


  // (Where did I intend this to be called?  Near add-ppt-conditional,
  // presumably.)
  public void addImplications() {
    int num_conds = views_cond.size();
    if (num_conds > 0) {
      // System.out.println("num_conds = " + num_conds);
      // for (int i=0; i<num_conds; i++) {
      //   System.out.println(((PptConditional)views_cond.elementAt(i)).name);
      // }
      // Assert.assert(num_conds == 2);
      PptConditional cond1 = (PptConditional) views_cond.elementAt(0);
      PptConditional cond2 = (PptConditional) views_cond.elementAt(1);
      addImplications_internal(cond1, cond2, false);
    } else if (this.ppt_name.isCombinedExitPoint()) {
      Vector exits = this.entry_ppt.exit_ppts;
      if (exits.size() == 2) {
        // Eventually I ought to make this applicable when the number of
        // individual exits is not 2.

        // System.out.println("num exits = " + exits.size());
        // for (int i=0; i<exits.size(); i++) {
        //   System.out.println(((PptTopLevel)exits.elementAt(i)).name);
        // }
        Assert.assert(exits.size() == 2, "Bad number of exits: " + exits.size());
        PptTopLevel ppt1 = (PptTopLevel) exits.elementAt(0);
        PptTopLevel ppt2 = (PptTopLevel) exits.elementAt(1);
        // No longer necessary to use add_implications, as we are now
        // adding combined prgram points early.
        // addImplications_internal(ppt1, ppt2, true);
        addImplications_internal(ppt1, ppt2, false);
      }
    } else {
      // System.out.println("No implications to add for " + this.name);
    }
  }


  private void addImplications_internal(Ppt ppt1, Ppt ppt2, boolean add_nonimplications) {
    // System.out.println("addImplications_internal: " + ppt1.name + ", " + ppt2.name);

    PptSlice[][] matched_views = match_views(ppt1, ppt2);
    if (debugAddImplications.isDebugEnabled()) {
      debugAddImplications.debug("Matched views=" + matched_views.length + " from " +
				 ppt1.views.size() + ", " + ppt2.views.size());
    }

    Vector exclusive_conditions_vec = new Vector(); // elements are pairs of Invariants
    Vector same_invariants_vec = new Vector(); // elements are Invariants

    for (int i=0; i<matched_views.length; i++) {
      PptSlice slice1 = matched_views[i][0];
      PptSlice slice2 = matched_views[i][1];

      if ((slice1 == null) || (slice2 == null)) {
        if (debugAddImplications.isDebugEnabled()) {
          debugAddImplications.debug("addImplications: matched views skipped "
				     + (slice1 == null ? "null" : slice1.name) + " "
				     + (slice2 == null ? "null" : slice2.name));
        }
        continue;
      }

      // Do not eliminate invariants that are not worth printing at this
      // stage!  Perhaps x=y is not worth printing because it is true at a
      // controller, but x!=y is worth printing; now we can't determine
      // that there is an exclusive condition.  We'll eliminate those
      // not-worth-printing invariants later, when we actually make the
      // implication invariants.

      Invariants invs1 = new Invariants();
      for (int j=0; j<slice1.invs.size(); j++) {
        Invariant inv = (Invariant)slice1.invs.elementAt(j);
        invs1.add(inv);
        if (debugAddImplications.isDebugEnabled()) {
          debugAddImplications.debug("invs1 " + inv.format());
        }
      }
      Invariants invs2 = new Invariants();
      for (int j=0; j<slice2.invs.size(); j++) {
        Invariant inv = (Invariant)slice2.invs.elementAt(j);
        invs2.add(inv);
        if (debugAddImplications.isDebugEnabled()) {
          debugAddImplications.debug("invs2 " + inv.format());
        }
      }

      Vector this_excl = exclusive_conditions(invs1, invs2);
      if (debugAddImplications.isDebugEnabled()) {
        debugAddImplications.debug("addImplications: "
				   + this_excl.size() + " exclusive conditions for "
				   + slice1.name + " " + slice2.name);
      }
      exclusive_conditions_vec.addAll(this_excl);

      Vector this_same = same_invariants(invs1, invs2);
      same_invariants_vec.addAll(this_same);
    }

    if (add_nonimplications) {
      for (int i=0; i<same_invariants_vec.size(); i++) {
        Invariant same_inv = (Invariant)same_invariants_vec.elementAt(i);
        // This test doesn't seem to be productive.  (That comment may date
        // from the time that all not-worth-printing invariants were
        // already eliminated.)
        if (! same_inv.isControlled()) {
          implication_view.addInvariant(same_inv);
        }
      }
    }

    if (exclusive_conditions_vec.size() == 0) {
      if (debugAddImplications.isDebugEnabled()) {
        debugAddImplications.debug("addImplications: no exclusive conditions");
      }
      return;
    }

    // These two program points are mutually exclusive

    Invariant[][] exclusive_conditions
      = (Invariant[][])exclusive_conditions_vec.toArray(new Invariant[0][0]);
    Invariant[][] different_invariants
      = (Invariant[][])different_invariants(matched_views).toArray(new Invariant[0][0]);

    if (debugAddImplications.isDebugEnabled()) {
      debugAddImplications.debug("addImplications: "
				 + exclusive_conditions.length + " exclusive conditions, "
				 + different_invariants.length + " different invariants");
    }


    // Add an implication from each of a pair of mutually exclusive
    // invariants to everything that differs (at all) about the two

    // split into two in order to use indexOf
    Invariant[] excls1 = new Invariant[exclusive_conditions.length];
    Invariant[] excls2 = new Invariant[exclusive_conditions.length];
    for (int i=0; i<exclusive_conditions.length; i++) {
      excls1[i] = exclusive_conditions[i][0];
      excls2[i] = exclusive_conditions[i][1];
    }


    for (int i=0; i<exclusive_conditions.length; i++) {
      Assert.assert(exclusive_conditions[i].length == 2);
      Invariant excl1 = exclusive_conditions[i][0];
      Invariant excl2 = exclusive_conditions[i][1];
      Assert.assert(excl1 != null);
      Assert.assert(excl2 != null);

      if (debugAddImplications.isDebugEnabled()) {
        debugAddImplications.debug("Adding implications with conditions "
				   + excl1.format() + " and " + excl2.format());
      }

      for (int j=0; j<different_invariants.length; j++) {
        Assert.assert(different_invariants[j].length == 2);
        Invariant diff1 = different_invariants[j][0];
        Invariant diff2 = different_invariants[j][1];

        Assert.assert((diff1 == null) || (diff2 == null)
                      || (ArraysMDE.indexOf(excls1, diff1)
                          == ArraysMDE.indexOf(excls2, diff2)));

        if (debugAddImplications.isDebugEnabled()) {
          debugAddImplications.debug("different_invariants "
				     + ((diff1 == null) ? "null" : diff1.format())
				     + ", " + ((diff2 == null) ? "null" : diff2.format()));
        }

        // This adds an implication to itself; bad.
        // If one of the diffs implies the other, then should not add
        // an implication for the weaker one.
        if (diff1 != null) {
          int index1 = ArraysMDE.indexOf(excls1, diff1);
          if ((index1 == -1) || (index1 > i)) {
            boolean iff = (index1 != -1);
            Implication.makeImplication(this, excl1, diff1, iff);
          }
        }
        if (diff2 != null) {
          int index2 = ArraysMDE.indexOf(excls2, diff2);
          if ((index2 == -1) || (index2 > i)) {
            boolean iff = (index2 != -1);
            Implication.makeImplication(this, excl2, diff2, iff);
          }
        }
      }
    }

    HashMap canonical_inv = new HashMap(); // Invariant -> Invariant
    {
      HashMap inv_group = new HashMap(); // Invariant -> HashSet[Invariant]

      // Problem: I am not iterating through the invariants in any particular
      // order that will guarantee that I don't see A and B, then C and D,
      // and then A and C (which both already have different canonical versions).
      // System.out.println(name + " implication canonicalization");
      for (Iterator itor = implication_view.invs.iterator(); itor.hasNext(); ) {
        Invariant inv = (Invariant) itor.next();
        if ((inv instanceof Implication) && ((Implication) inv).iff) {
          Implication impl = (Implication) inv;
          // System.out.println("Bi-implication: " + impl.format());
          Invariant canon1 = (Invariant) canonical_inv.get(impl.predicate);
          Invariant canon2 = (Invariant) canonical_inv.get(impl.consequent);
          if ((canon1 != null) && (canon2 != null) && (canon1 != canon2)) {
            // Move all the invariants for canon2 over to canon1
            HashSet hs1 = (HashSet) inv_group.get(canon1);
            HashSet hs2 = (HashSet) inv_group.get(canon2);
            inv_group.remove(canon2);
            for (Iterator itor2=hs2.iterator(); itor2.hasNext(); ) {
              Invariant inv2 = (Invariant) itor2.next();
              hs1.add(inv2);
              canonical_inv.put(inv2, canon1);
            }
            // System.out.print("Current set:");
            // for (Iterator itor2=hs1.iterator(); itor2.hasNext(); ) {
            //   Invariant inv2 = (Invariant) itor2.next();
            //   System.out.print("    " + inv2.format());
            // }
            // System.out.println();
          } else {
            Invariant canon = (canon1 != null) ? canon1 : (canon2 != null) ? canon2 : impl.predicate;
            // System.out.println("Canonical: " + canon.format());
            canonical_inv.put(impl.predicate, canon);
            canonical_inv.put(impl.consequent, canon);
            HashSet hs = (HashSet) inv_group.get(canon);
            if (hs == null) {
              hs = new HashSet();
              inv_group.put(canon, hs);
            }
            hs.add(impl.predicate);
            hs.add(impl.consequent);
            // System.out.print("Current set (2):");
            // for (Iterator itor2=hs.iterator(); itor2.hasNext(); ) {
            //   Invariant inv2 = (Invariant) itor2.next();
            //   System.out.print("    " + inv2.format());
            // }
            // System.out.println();
          }
        }
      }

      // Now adjust which of the invariants are canonical.
      // (That is why inv_group was computed above.)

      for (Iterator itor=inv_group.keySet().iterator(); itor.hasNext(); ) {
        Invariant canon_orig = (Invariant) itor.next();
        // System.out.println("Outer loop: " + canon_orig.format());
        HashSet hs = (HashSet) inv_group.get(canon_orig);
        if (hs.size() == 1) {
          continue;
        }
        Invariant canon_new = null;
        String canon_new_formatted = null;
        for (Iterator cand_itor=hs.iterator(); cand_itor.hasNext(); ) {
          Invariant candidate = (Invariant) cand_itor.next();
          String candidate_formatted = candidate.format();
          // System.out.println("Comparing:" + lineSep + "    " + candidate_formatted + lineSep + "    " + canon_new_formatted);
          // It is also desirable to be over the prestate;
          // but that is only true for variables that are modified.
          // A variable without "orig()" is fine if it's not modified.
          boolean canon_new_undesirable
            = ((canon_new == null) // avoid NullPointerException
               || (canon_new_formatted.indexOf("\"null\"") != -1)
               || (canon_new_formatted.indexOf("return") != -1));
          boolean candidate_undesirable
            = ((candidate_formatted.indexOf("\"null\"") != -1)
               || (candidate_formatted.indexOf("return") != -1));
          if ((canon_new == null)
              || canon_new_undesirable
              || ((! candidate_undesirable)
                  && (candidate_formatted.length() < canon_new_formatted.length()))) {
            canon_new = candidate;
            canon_new_formatted = candidate_formatted;
          }
        }
        if (canon_new != canon_orig) {
          // Don't set inv_group, lest I get a ConcurrentModificationException
          // inv_group.put(canon_new, hs);
          // inv_group.remove(canon_orig);
          for (Iterator inv_itor=hs.iterator(); inv_itor.hasNext(); ) {
            Invariant inv = (Invariant) inv_itor.next();
            Assert.assert(canonical_inv.get(inv) == canon_orig);
            canonical_inv.put(inv, canon_new);
          }
        }
      }
      // inv_group is no longer up-to-date now.
      // I could have created an inv_group_2 during the above computation
      // and set inv_group to it if I liked.
    }

    // Prune out implications over non-canonical invariants

    Vector to_remove = new Vector();
    for (Iterator itor = implication_view.invs.iterator(); itor.hasNext(); ) {
      Invariant inv = (Invariant) itor.next();
      if (inv instanceof Implication) {
        Implication impl = (Implication) inv;
        Invariant cpred = (Invariant) canonical_inv.get(impl.predicate);
        Invariant ccons = (Invariant) canonical_inv.get(impl.consequent);
        boolean pred_non_canon = ((cpred != null) && (impl.predicate != cpred));
        boolean cons_non_canon = ((ccons != null) && (impl.consequent != ccons));
        if ((! impl.iff)
            && (pred_non_canon || cons_non_canon)) {
          to_remove.add(inv);
        }
      }
    }
    implication_view.invs.removeAll(to_remove);


    // System.out.println("Done adding no more than "
    //                    + (exclusive_conditions.length * different_invariants.length)
    //                    + " implications.");

  }


  // Match up the slices in the two program points.
  // Each element is a PptSlice[2].
  // (Perhaps I need to do something special in the case of differing canonical
  // variables; deal with that later.)
  public PptSlice[][] match_views(Ppt ppt1, Ppt ppt2) {
    Vector result = new Vector();

    SortedSet ss1 = new TreeSet(arityVarnameComparator);
    ss1.addAll(ppt1.views);
    SortedSet ss2 = new TreeSet(arityVarnameComparator);
    ss2.addAll(ppt2.views);

    for (OrderedPairIterator opi = new OrderedPairIterator(ss1.iterator(), ss2.iterator(), arityVarnameComparator); opi.hasNext(); ) {
      Pair pair = (Pair) opi.next();
      result.add(new PptSlice[] { (PptSlice) pair.a, (PptSlice) pair.b });
    }
    return (PptSlice[][])result.toArray(new PptSlice[0][0]);
  }


  // Determine which elements of invs1 are mutually exclusive with elements
  // of invs2.
  // Result elements are pairs of Invariants.
  Vector exclusive_conditions(Invariants invs1, Invariants invs2) {
    Vector result = new Vector();
    for (int i1=0; i1<invs1.size(); i1++) {
      for (int i2=0; i2<invs2.size(); i2++) {
        Invariant inv1 = (Invariant) invs1.elementAt(i1);
        Invariant inv2 = (Invariant) invs2.elementAt(i2);
        // This is a debugging tool, to make sure that various versions
        // of isExclusiveFormula remain coordinated.  (That's also one
        // reason we don't break out of the loop early:  also, there will
        // be few invariants in a slice.)
        Assert.assert(inv1.isExclusiveFormula(inv2)
                      == inv2.isExclusiveFormula(inv1),
                      "Bad exclusivity: " + inv1.isExclusiveFormula(inv2) + " " + inv2.isExclusiveFormula(inv1)
                       + "    " + inv1.format() + "    " + inv2.format());
        // System.out.println("isExclusiveFormula(" + inv1.format() + ", " + inv2.format() + ") = " + inv1.isExclusiveFormula(inv2));
        if (inv1.isExclusiveFormula(inv2)) {
          result.add(new Invariant[] { inv1, inv2 });
        }
      }
    }
    return result;
  }


  // Different_invariants and same_invariants should be merged.
  // They are used by the code that adds implications.


  // Determine which elements of invs1 differ from elements of invs2.
  // Result elements are pairs of Invariants (with one or the other
  // possibly null).
  Vector different_invariants(Invariants invs1, Invariants invs2) {
    SortedSet ss1 = new TreeSet(icfp);
    // ss1.addAll(invs1);
    for (int j=0; j<invs1.size(); j++) {
      Invariant inv = (Invariant)invs1.elementAt(j);
      ss1.add(inv);
    }

    SortedSet ss2 = new TreeSet(icfp);
    for (int j=0; j<invs2.size(); j++) {
      Invariant inv = (Invariant)invs2.elementAt(j);
      ss2.add(inv);
    }

    Vector result = new Vector();
    for (OrderedPairIterator opi = new OrderedPairIterator(ss1.iterator(), ss2.iterator(), icfp); opi.hasNext(); ) {
      Pair pair = (Pair) opi.next();
      if ((pair.a == null) || (pair.b == null) || (icfp.compare(pair.a, pair.b) != 0)) {
        result.add(new Invariant[] { (Invariant) pair.a, (Invariant) pair.b });
      }
    }
    return result;
  }


  // Determine which invariants at the program points differ.
  // Result elements are pairs of Invariants (with one or the other
  // possibly null.)
  Vector different_invariants(PptSlice[][] matched_views) {
    Vector result = new Vector();
    for (int i=0; i<matched_views.length; i++) {
      PptSlice cond1 = matched_views[i][0];
      PptSlice cond2 = matched_views[i][1];
      Invariants invs1 = (cond1 == null) ? new Invariants() : cond1.invs;
      Invariants invs2 = (cond2 == null) ? new Invariants() : cond2.invs;
      result.addAll(different_invariants(invs1, invs2));
    }
    return result;
  }


  // Determine which elements of invs1 are the same as elements of invs2.
  // Result elements are Invariants.
  Vector same_invariants(Invariants invs1, Invariants invs2) {
    Vector result = new Vector();
    SortedSet ss1 = new TreeSet(icfp);
    ss1.addAll(invs1);
    SortedSet ss2 = new TreeSet(icfp);
    ss2.addAll(invs2);
    for (OrderedPairIterator opi = new OrderedPairIterator(ss1.iterator(), ss2.iterator(), icfp); opi.hasNext(); ) {
      Pair pair = (Pair) opi.next();
      if (pair.a != null && pair.b != null) {
        Invariant inv1 = (Invariant) pair.a;
        Invariant inv2 = (Invariant) pair.b;
        if (inv1.enoughSamples() && inv2.enoughSamples()) {
          result.add(inv1);
        }
      }
    }
    return result;
  }


  ///////////////////////////////////////////////////////////////////////////
  /// Locating implied (same) invariants
  ///

  // Created upon first use, then saved
  private static SessionManager prover = null;
  private static String prover_background = null;
  public static int prover_instantiate_count = 0;

  private static String prover_background() {
    if (prover_background == null) {
      try {
	StringBuffer result = new StringBuffer("(AND " + Global.lineSep);
	InputStream bg_stream = PptTopLevel.class.getResourceAsStream("simplify/daikon-background.txt");
	Assert.assert(bg_stream != null, "Could not find simplify/daikon-background.txt");
	BufferedReader lines = new BufferedReader(new InputStreamReader(bg_stream));
	String line;
	while ((line = lines.readLine()) != null) {
	  line = line.trim();
	  if (line.length() == 0) continue;
	  if (line.startsWith(";")) continue;
	  result.append(" ");
	  result.append(line);
	  result.append(Global.lineSep);
	}
	result.append(")");
	prover_background = result.toString();
      } catch (IOException e) {
	Assert.assert(false, "Could not load prover background");
      }
    }
    return prover_background;
  }

  // Start up simplify, and send the universal backgound.
  // Is successful exactly when this.prover != null.
  private static void attempt_prover_startup()
  {
    // If already started, we are fine
    if (prover != null) {
      return;
    }

    // Limit ourselves to a few tries
    if (prover_instantiate_count > 5) {
      return;
    }

    // Start the prover
    try {
      prover_instantiate_count++;
      prover = new SessionManager();
      if (Daikon.no_text_output) {
	System.out.print("...");
      }
    } catch (SimplifyError e) {
      System.err.println("Could not utilize Simpilify: " + e);
      return;
    }

    try {
      prover.request(new CmdAssume(prover_background()));
    } catch (TimeoutException e) {
      throw new RuntimeException("Timeout on universal background " + e);
    }
  }

  /**
   * Interface used by mark_implied_via_simplify to determine what
   * invaraints should be considered during the logical redundancy
   * tests.
   **/
  public static interface SimplifyInclusionTester {
    public boolean include(Invariant inv);
  }

  /**
   * Use the Simplify theorem prover to flag invaraints which are
   * logically implied by others.  Considers only invariants which
   * pass isWorthPrinting.
   **/
  public void mark_implied_via_simplify() {
    mark_implied_via_simplify(new SimplifyInclusionTester() {
	public boolean include(Invariant inv) {
	  return inv.isWorthPrinting();
	}
      });
  }

  /**
   * Use the Simplify theorem prover to flag invaraints which are
   * logically implied by others.  Uses the provided test interface to
   * determine if an invariant is within the domain of inspection.
   **/
  public void mark_implied_via_simplify(SimplifyInclusionTester test) {
    SessionManager.debugln("Simplify checking " + ppt_name);

    // Create the list of invariants from this ppt which are
    // expressible in Simplify
    Invariant[] invs;
    {
      // Replace parwise equality with an equivalence sets
      Collection all = InvariantFilters.addEqualityInvariants(invariants_vector());
      Vector printing = new Vector(); // [Invariant]
      for (Iterator _invs = all.iterator(); _invs.hasNext(); ) {
	Invariant inv = (Invariant) _invs.next();
	if (test.include(inv)) { // think: inv.isWorthPrinting()
	  String fmt = inv.format_simplify();
	  if (fmt.indexOf("format_simplify") < 0) {
	    printing.add(inv);
	  }
	}
      }
      invs = (Invariant[]) printing.toArray(new Invariant[printing.size()]);
    }

    // For efficiency, bail if we don't have any invariants to mark as implied
    if (invs.length == 0) {
      return;
    }

    // Come up with a "desirability" ordering of the printing and
    // expressible invariants, so that we can remove the least
    // desirable first.  For now just use the ICFP.
    Arrays.sort(invs, icfp);

    // Debugging
    if (Global.debugSimplify.isDebugEnabled()) {
      Global.debugSimplify.debug("Sorted invs:");
      for (int i=0; i<invs.length; i++) {
        Global.debugSimplify.debug("    " + invs[i].format());
      }
      for (int i=0; i<invs.length-1; i++) {
        int cmp = icfp.compare(invs[i], invs[i+1]);
        Global.debugSimplify.debug("cmp(" + i + "," + (i+1) + ") = " + cmp);
        int rev_cmp = icfp.compare(invs[i+1], invs[i]);
        Global.debugSimplify.debug("cmp(" + (i+1) + "," + i + ") = " + rev_cmp);
        Assert.assert(rev_cmp >= 0);
      }
    }

    // Form the closure of the controllers
    Set closure = new HashSet();
    {
      Set working = new HashSet(controlling_ppts);
      while (!working.isEmpty()) {
	PptTopLevel ppt = (PptTopLevel) working.iterator().next();
	working.remove(ppt);
	if (!closure.contains(ppt)) {
	  closure.add(ppt);
	  working.addAll(ppt.controlling_ppts);
	}
      }
    }

    // Create the conjunction of the closures' invariants to form a
    // background environment for the prover.  Ignore implications,
    // since in the current scheme, implications came from controlled
    // program points, and we don't necessarily want to lose the
    // unconditoinal version of the invariant at the conditional ppt.
    StringBuffer all_cont = new StringBuffer();
    all_cont.append("(AND \n");
    for (Iterator ppts = closure.iterator(); ppts.hasNext(); ) {
      PptTopLevel ppt = (PptTopLevel) ppts.next();
      all_cont.append("\t(AND \n");
      Iterator _invs = InvariantFilters.addEqualityInvariants(ppt.invariants_vector()).iterator();
      while(_invs.hasNext()) {
	Invariant inv = (Invariant) _invs.next();
	if (inv instanceof Implication) {
	  continue;
	}
	if (!test.include(inv)) { // think: !inv.isWorthPrinting()
	  continue;
	}
	String fmt = inv.format_simplify();
	if (fmt.indexOf("format_simplify") >= 0) {
	  continue;
	}
	// We could also consider testing if the controlling invariant
	// was removed by Simplify, but what would be point be?  Also,
	// these "intermediate goals" might help out Simplify.
	all_cont.append("\t\t");
	all_cont.append(fmt);
	all_cont.append("\n");
	// If this is the :::OBJECT ppt, also restate all of them in
	// orig terms, since we the conditions also held upon entry.
	if (ppt.ppt_name.isObjectInstanceSynthetic()) {
	  // XXX This isn't such a hot thing to do, but it isn't that
	  // hard, and seems to work.
	  PptSlice saved = inv.ppt;
	  PptSlice orig = new PptSlice0(saved.parent);
	  orig.var_infos = new VarInfo[saved.var_infos.length];
	  for (int i=0; i<orig.var_infos.length; i++) {
	    orig.var_infos[i] = VarInfo.origVarInfo(saved.var_infos[i]);
	  }
	  inv.ppt = orig;
	  all_cont.append("\t\t");
	  all_cont.append(inv.format_simplify());
	  all_cont.append("\n");
	  inv.ppt = saved;
	}
      }
      all_cont.append(")");
    }

    // Restate OBJECT invariants on incoming arguments of our same type
    if (ppt_name.isEnterPoint() && controlling_ppts.size() == 1) {
      // Guess the OBJECT ppt; usually right
      PptTopLevel OBJ = (PptTopLevel) controlling_ppts.iterator().next();
      if (OBJ.ppt_name.isObjectInstanceSynthetic()) {
	// Find variables here of the same type as us
	String clsname = ppt_name.getFullClassName();
	for (int i=0; i < var_infos.length; i++) {
	  VarInfo vi = var_infos[i];
	  String progtype = vi.type.base();
	  // System.out.println("i=" + i + " of " + var_infos.length + "; base = " + progtype + "; cls = " + clsname);
	  if (progtype.equals(clsname)) {
	    // Only process primitive names like 'x'; not 'x.foo' or 'a[x..]'
	    if (vi.name.inOrderTraversal().size() != 1) {
	      System.err.println("Object invariants on argument: skipping " + vi.name.name());
	      continue;
	    }
	    // State the object invariant on the incoming argument
	    all_cont.append("\t(AND \n");
	    Iterator _invs = InvariantFilters.addEqualityInvariants(OBJ.invariants_vector()).iterator();
	    while(_invs.hasNext()) {
	      Invariant inv = (Invariant) _invs.next();
	      if (!test.include(inv)) { // think: !inv.isWorthPrinting()
		continue;
	      }
	      String fmt = inv.format_simplify();
	      if (fmt.indexOf("format_simplify") >= 0) {
		continue;
	      }
	      // XXX This isn't such a hot thing to do, but it isn't that
	      // hard, and seems to work.
	      PptSlice saved = inv.ppt;
	      PptSlice rewritten = new PptSlice0(saved.parent);
	      rewritten.var_infos = new VarInfo[saved.var_infos.length];
	      for (int x=0; x<rewritten.var_infos.length; x++) {
		VarInfo svi = saved.var_infos[x];
		rewritten.var_infos[x] =
		  new VarInfo(svi.name.replaceAll(VarInfoName.parse("this"), vi.name),
			      svi.type, svi.file_rep_type,
			      svi.comparability.makeAlias(svi.name));
	      }
	      inv.ppt = rewritten;
	      all_cont.append("\t\t");
	      all_cont.append(inv.format_simplify());
	      all_cont.append("\n");
	      inv.ppt = saved;
	    }
	    all_cont.append(")");
	  }
	}
      }
    }

    all_cont.append(")");
    CmdAssume background = new CmdAssume(all_cont.toString());

    // Send the background to the prover
    try {
      attempt_prover_startup();
      if (prover == null) return;
      prover.request(background);
    } catch (TimeoutException e) {
      prover = null;
      return;
    }

    // Work from back to front, and flag things which are redundant
    boolean[] present = new boolean[invs.length];
    Arrays.fill(present, 0, present.length, true);
    for (int checking = invs.length-1; checking >= 0; checking--) {
      Invariant inv = invs[checking];
      StringBuffer bg = new StringBuffer("(AND ");
      for (int i=0; i < present.length; i++) {
	if (present[i] && (i != checking)) {
	  bg.append(" ");
          // format_simplify() is guaranteed to return a sensible result
          // for invariants in invs[].
	  bg.append(invs[i].format_simplify());
	}
      }
      bg.append(")");

      // Debugging
      if (Global.debugSimplify.isDebugEnabled()) {
      SessionManager.debugln("Background:");
      for (int i=0; i < present.length; i++) {
        if (present[i] && (i != checking)) {
          SessionManager.debugln("    " + invs[i].format());
        }
      }
      }

      try {
	// If the background is necessarily false, we are in big trouble
	CmdCheck bad = new CmdCheck("(NOT " + bg + ")");
	attempt_prover_startup();
	if (prover == null) return;
	prover.request(bad);
	if (bad.valid) {
	  // BAD!!
	  System.err.println("Warning: " + ppt_name + " invariants are contradictory; punting!");
	  return;
	}

	// The background wasn't necessarily false; see if it implies
	// the invariant under test.
	String ask = "(IMPLIES " + bg + " " + inv.format_simplify() + ")";
	CmdCheck cc = new CmdCheck(ask); // result is initialized to false
	prover.request(cc);
	if (cc.valid) {
	  // ick ick ick
	  if (inv instanceof Equality) {
	    // Equality is not represented with a permanent invariant
	    // object, so store the canonical variable instead.

            // // Debugging
            // System.out.println("Adding redundant var " + ((Equality) inv).leader().name.name() + " due to " + inv.format());
            // System.out.println("Background = ");
            // for (int i=0; i < present.length; i++) {
            //   if (i == checking) {
            //     System.out.println("  <<<this invariant not in its own background>>>");
            //   }
            //   if (present[i] && (i != checking)) {
            //     System.out.println("  " + invs[i].format() + "\t" + invs[i].getClass().getName());
            //   }
            // }

	    redundant_invs.add(((Equality) inv).leader());
	  } else {
	    redundant_invs.add(inv);
	  }
	  present[checking] = false;
	}
	SessionManager.debugln((present[checking] ? "UNIQUE" : "REDUNDANT") + " " + invs[checking].format());
      } catch (TimeoutException e) {
	// Reset the prover with the controlling invariant background
	prover = null;
	attempt_prover_startup();
	if (prover == null) return;
	try {
	  prover.request(background);
	} catch (TimeoutException f) {
	  prover = null;
	  return;
	}
      }
    }

    // Remove the controlling invariant background
    try {
      prover.request(CmdUndoAssume.single);
    } catch (TimeoutException e) {
      prover = null;
    }
  }


  ///////////////////////////////////////////////////////////////////////////
  /// Printing invariants
  ///

  // This is a fairly inefficient method, as it does a lot of copying.
  // As of 1/9/2000, this is only used in print_invariants.
  /**
   * Return a vector of all the invariants for the program point.
   * Also consider using views_iterator() instead.
   **/
  public Vector invariants_vector() {
    Vector result = new Vector();
    for (Iterator views_itor = views.iterator(); views_itor.hasNext(); ) {
      PptSlice slice = (PptSlice) views_itor.next();
      result.addAll(slice.invs);
    }
    // System.out.println(implication_view.invs.size() + " implication invs for " + name + " at " + implication_view.name);
    result.addAll(implication_view.invs);
    return result;
  }

  /**
   * For some clients, this method may be more efficient than invariants_vector.
   **/
  public Iterator views_iterator() {
    return views.iterator();
  }

  public Iterator invariants_iterator() {
    return new UtilMDE.MergedIterator(views_iterator_iterator());
  }

  private Iterator views_iterator_iterator() {
    return new ViewsIteratorIterator(this);
  }

  /** An iterator whose elements are themselves iterators that return invariants. **/
  public static final class ViewsIteratorIterator implements Iterator {
    Iterator vitor;
    public ViewsIteratorIterator(PptTopLevel ppt) { vitor = ppt.views_iterator(); }
    public boolean hasNext() { return vitor.hasNext(); }
    public Object next() { return ((PptSlice)vitor.next()).invs.iterator(); }
    public void remove() { throw new UnsupportedOperationException(); }
  }


  // In original (Python) implementation, known as print_invariants_ppt.
  // I may still want to integrate some more of its logic here.
  /**
   * Print invariants for a single program point.
   * Does no output if no samples or no views.
   **/
  public void print_invariants_maybe(PrintStream out, PptMap all_ppts) {
    // Maybe this test isn't even necessary, but will be subsumed by others
    // (as all the invariants will be unjustified).
    if (! has_samples()) {
      out.println("No samples for " + name);
      return;
    }
    if ((views.size() == 0) && (implication_view.invs.size() == 0)) {
      if (! (this instanceof PptConditional)) {
        // Presumably all the views that were originally there were deleted
        // because no invariants remained in any of them.
        if (Daikon.output_num_samples) {
          out.println("[No views for " + name + "]");
        }
      }
      return;
    }
    if ((combined_exit != null) && (Daikon.output_style != Daikon.OUTPUT_STYLE_NORMAL)) {
      if (Daikon.output_num_samples) {
        out.println("[Is combined exit, output style " + Daikon.output_style + ": " + name + "]");
      }
      return;
    }
    /// Old, more broken version.
    // // This suppression test does not work, because even if :::EXIT exists,
    // // it doesn't yet have any implication invariants, and we won't know
    // // about those until we process it in this loop.
    // // Do not print if this is :::EXIT22 and :::EXIT exists
    // if (Daikon.esc_output
    //     && ppt_name.isExitPoint()
    //     && (!ppt_name.exitLine().equals(""))) {
    //   String exitname = ppt_name.makeExit().getName();
    //   PptTopLevel exit = (PptTopLevel) all_ppts.get(exitname);
    //   // Don't suppress if the :::EXIT point has no invariants.
    //   // This could happen if :::EXIT1 was executed but :::EXIT2 never was.
    //   // Or, it could happen if it shouldn't have any invariants!
    //   if (exit != null) {
    //     if (!((exit.views.size() == 0) && (exit.implication_view.invs.size() == 0))) {
    //       System.out.println("Suppressing " + name + " in favor of " + exitname);
    //       return;
    //     }
    //     System.out.println("Not suppressing " + name + " in favor of " + exitname + ": " + " exit has " + exit.views.size() + " views and " + views_cond.size() + " conditional views and " + exit.implication_view.invs.size() + " implication invs" + " for a total of " + exit.invariants_vector().size() + " invariants");
    //   } else {
    //     // System.out.println("Didn't find unified " + exitname + " for " + name + ", so doing output for " + name);
    //   }
    // } else {
    //   // System.out.println("Not an exit, or exitline is \"\": " + name + "; " + ppt_name.isExitPoint() + " <<<" + ppt_name.exitLine() + ">>>");
    // }


    // out.println("This = " + this + ", Name = " + name + " = " + ppt_name);

    out.println("===========================================================================");
    print_invariants(out);

    if (Daikon.dkconfig_output_conditionals && Daikon.output_style == Daikon.OUTPUT_STYLE_NORMAL) {
      for (int i=0; i<views_cond.size(); i++) {
        PptConditional pcond = (PptConditional) views_cond.elementAt(i);
        pcond.print_invariants_maybe(out, all_ppts);
      }
    }

  }

  boolean check_modbits () {
    // This test is wrong for PptTopLevel because something is considered
    // unmodified only if none of its values are modified.  The test is
    // appropriate for PptSlice because we don't put missing values
    // there.

    // // The value "0" can be had for missing samples.
    // if (num_mod_non_missing_samples() < num_values() - 1) {
    //   throw new Error("Bad mod bits in dtrace file:" + lineSep
    //                   + "num_mod_non_missing_samples()=" + num_mod_non_missing_samples()
    //                   + ", num_values()=" + num_values() + lineSep
    //                   + "for " + name + lineSep
    //                   + tuplemod_samples_summary() + lineSep
    //                   + "Consider running modbit-munge.pl");
    // }

    return true;
  }

  ///////////////////////////////////////////////////////////////////////////
  /// Printing invariants
  ///

  /**
   * Simplify the names of variables before printing them.  For
   * example, "orig(a[post(i)])" might change into "orig(a[i+1])".  We
   * might want to switch off this behavior, depending on various
   * heuristics.  We'll have to try it and see which output we like
   * best.  In any case, we have to do this for ESC output, since ESC
   * doesn't have anything like post().
   **/
  public void simplify_variable_names() {
    Iterator iter = Arrays.asList(var_infos).iterator();
    while (iter.hasNext()) {
      VarInfo vi = (VarInfo) iter.next();
      vi.simplify_expression();
    }
  }

  // To avoid the leading "UtilMDE." on all calls.
  private static String nplural(int n, String noun) {
    return UtilMDE.nplural(n, noun);
  }

  final public static Comparator icfp = new Invariant.InvariantComparatorForPrinting();

  /** Print invariants for a single program point. */
  public void print_invariants(PrintStream out) {

    // make names easier to read before printing
    simplify_variable_names();

    // System.out.println("This = " + this + ", Name(2) = " + name + " = " + ppt_name);
    String better_name = name;
    {
      // Replace <init> with name of class
      int init_pos = better_name.indexOf(".<init>");
      if (init_pos != -1) {
	String before = better_name.substring(0, init_pos);
	String after = better_name.substring(init_pos+7);
	String classname = before;
	int lastdot = before.lastIndexOf('.'); // Not corrent for inners, but oh well
	if (lastdot >= 0) {
	  classname = before.substring(lastdot+1);
	}
	better_name = before + "." + classname + after;
      }
    }
    int open_paren_pos = better_name.indexOf("(");
    if ((open_paren_pos != -1)
        // if open paren is first character, don't try to be clever
        && (open_paren_pos != 0)) {
      int close_paren_pos = better_name.indexOf(")");
      int colon_pos = better_name.indexOf(":::");
      String args = better_name.substring(open_paren_pos, close_paren_pos+1);
      String java_args = UtilMDE.arglistFromJvm(args);
      better_name = better_name.substring(0, open_paren_pos)
        + ((java_args != null) ? java_args : args)
        + better_name.substring(colon_pos);
    }

    // IOA, quick fix
    String classname = better_name.substring(0, better_name.indexOf(":::"));
    classname = (classname.indexOf(".")==-1) ? classname :
      classname.substring(0, classname.indexOf("."));
    // end IOA

    if (Daikon.output_num_samples) {
      int num_samps = num_samples();
      out.println(better_name + "  " + nplural(num_samps, "sample"));
      out.println("    Samples breakdown: " + tuplemod_samples_summary());
    } else {
      out.println(better_name);
    }
    if (Daikon.output_num_samples || (Daikon.output_style == Daikon.OUTPUT_STYLE_ESC)) {
      out.print("    Variables:");
      for (int i=0; i<var_infos.length; i++)
        out.print(" " + var_infos[i].name);
      out.println();
    }
    Vector modified_vars = new Vector();
    Vector modified_primitive_vars = new Vector();
    Vector unmodified_vars = new Vector();
    Vector unmodified_orig_vars = new Vector();
    for (int i=0; i<var_infos.length; i++) {
      VarInfo vi = var_infos[i];
      // This test is purely an optimization.
      if (! vi.isPrestate()) {
        VarInfo vi_orig = findVar(vi.name.applyPrestate());
        if (vi_orig != null) {
          // Assert.assert(vi_orig.postState.name == vi.name, "vi_orig="+vi_orig.name+", vi_orig.postState="+vi_orig.postState+((vi_orig.postState!=null)?"="+vi_orig.postState.name:"")+", vi="+vi+"="+vi.name);
          // Assert.assert(vi_orig.postState == vi, "vi_orig="+vi_orig.name+", vi_orig.postState="+vi_orig.postState+((vi_orig.postState!=null)?"="+vi_orig.postState.name:"")+", vi="+vi+"="+vi.name);
          if (vi.equal_to == vi_orig.equal_to) {
            unmodified_vars.add(vi);
            unmodified_orig_vars.add(vi_orig);
          } else {
            // out.println("Modified: " + vi.name + " (=" + vi.equal_to.name + "), " + vi_orig.name + " (=" + vi_orig.equal_to.name + ")");
            PptSlice1 view = getView(vi);
            if ((view != null) && (view.num_values() > 0)) {
              // Using only the isPrimitive test is wrong.  We should suppress
	      // for only parameters, not all primitive values.
              if (vi.type.isPrimitive() && (vi.name.name().indexOf(".") == -1)) {
                modified_primitive_vars.add(vi);
              } else {
                modified_vars.add(vi);
              }
            }
          }
        }
      }
    }
    if (Daikon.output_num_samples || (Daikon.output_style == Daikon.OUTPUT_STYLE_ESC)) {
      if (modified_vars.size() > 0) {
        out.print("      Modified variables:");
        for (int i=0; i<modified_vars.size(); i++) {
          VarInfo vi = (VarInfo)modified_vars.elementAt(i);
          out.print(" " + vi.name);
        }
        out.println();
      }
      if (modified_primitive_vars.size() > 0) {
        out.print("      Modified primitive variables:");
        for (int i=0; i<modified_primitive_vars.size(); i++) {
          VarInfo vi = (VarInfo)modified_primitive_vars.elementAt(i);
          out.print(" " + vi.name);
        }
        out.println();
      }
      if (unmodified_vars.size() > 0) {
        out.print("      Unmodified variables:");
        for (int i=0; i<unmodified_vars.size(); i++)
          out.print(" " + ((VarInfo)unmodified_vars.elementAt(i)).name);
        out.println();
      }
    }

    Assert.assert(check_modbits());

    // Dump some debugging info, if enabled
    if (Invariant.debugPrint.isDebugEnabled()) {
      // out.println("Views:");
      // for (Iterator itor = views.iterator(); itor.hasNext(); ) {
      //   PptSlice slice = (PptSlice) itor.next();
      //   out.println("  " + slice.name);
      //   for (int i=0; i<slice.invs.size(); i++) {
      //     Invariant inv = (Invariant) slice.invs.elementAt(i);
      //     out.println("    " + inv.repr());
      //   }
      // }

      Invariant.debugPrint.debug("    Variables:");
      for (int i=0; i<var_infos.length; i++) {
        VarInfo vi = var_infos[i];
        PptTopLevel ppt_tl = (PptTopLevel) vi.ppt;
        PptSlice slice1 = ppt_tl.findSlice(vi);
        out.print("      " + vi.name
		  + " constant=" + vi.isConstant()
		  + " canonical=" + vi.isCanonical()
		  + " equal_to=" + vi.equal_to.name);
        // if (slice1 == null) {
        //   out.println(", no slice");
        // } else {
        //   out.println(" slice=" + slice1
        //               + "=" + slice1.name
        //               + " num_values=" + slice1.num_values()
        //               + " num_samples=" + slice1.num_samples());
        //   // slice1.values_cache.dump();
        // }
      }
    }

    // Count statistics on variables (canonical, missing, etc.)
    for (int i=0; i<var_infos.length; i++) {
      if (! var_infos[i].isCanonical()) {
        Global.non_canonical_variables++;
      } else if (var_infos[i].canBeMissingCheck()) {
        Global.can_be_missing_variables++;
      } else {
        Global.canonical_variables++;
      }
      if (var_infos[i].isDerived()) {
        Global.derived_variables++;
      }
    }

    // First, print the equality invariants.  They don't show up in the
    // below because one of the two variables is non-canonical!
    // This technique is a bit non-orthogonal, but probably fine.
    // We might do no output if all the other variables are vacuous.
    // We should have already equal_to for each VarInfo.

    for (int i=0; i<var_infos.length; i++) {
      VarInfo vi = var_infos[i];
      // System.out.println("Considering equality for "
      //                    + (vi.isCanonical() ? "" : "non")
      //                    + "canonical var " + vi.name.name());
      if (vi.isCanonical()) {

	// switch commented lines to include obviously equal in output
	Vector equal_vars = vi.equalToNonobvious();
	Vector obviously_equal = new Vector();
	// Vector equal_vars = vi.equalTo();
	// Vector obviously_equal = new Vector(equal_vars);
	// obviously_equal.removeAll(vi.equalToNonobvious());

        // System.out.println("equal_vars.size() = " + equal_vars.size());
        // System.out.println("Redundant due to simplify = "
        //                    + (Daikon.suppress_redundant_invariants_with_simplify
        //                       && redundant_invs.contains(vi)));

        if (equal_vars.size() > 0 &&
	    // suppress if the equality invariant is implied via simplify
	    (! (Daikon.suppress_redundant_invariants_with_simplify &&
		redundant_invs.contains(vi)))) {
	  switch (Daikon.output_style) {
          case Daikon.OUTPUT_STYLE_ESC:
            for (int j=0; j<equal_vars.size(); j++) {
              VarInfo other = (VarInfo) equal_vars.elementAt(j);
	      if (other.isDerivedSequenceMinMaxSum())
		break;
	      if (vi.rep_type.isArray()) {
		String[] form =
		  VarInfoName.QuantHelper.format_esc(new VarInfoName[]
		    { vi.name, other.name }, true); // elementwise
                out.println(form[0] + "( " + form[1] + " == " + form[2] + " )" + form[3]);
              } else {
		out.println(vi.name.esc_name() + " == " + other.name.esc_name());
	      }
	      if (obviously_equal.contains(other)) {
		out.println("    (obviously)");
	      }
            }
	    break;
          case Daikon.OUTPUT_STYLE_NORMAL:
            StringBuffer sb = new StringBuffer(vi.name.name());
            for (int j=0; j<equal_vars.size(); j++) {
              VarInfo other = (VarInfo) equal_vars.elementAt(j);
              sb.append(" == "); // "interned"
              sb.append(other.name);
	      if (obviously_equal.contains(other)) {
		sb.append(" (obviously)");
	      }
            }
            PptTopLevel ppt_tl = (PptTopLevel) vi.ppt;
            PptSlice slice1 = ppt_tl.findSlice(vi);
            if (Daikon.output_num_samples) {
              if (slice1 != null) {
                sb.append("\t\t(" +
                          nplural(slice1.num_values(), "value") + ", " +
                          nplural(slice1.num_samples(), "sample") + ")");
              } else {
                // sb.append("\t\t(no slice)");
              }
            }
            out.println(sb.toString());
	    break;
	  case Daikon.OUTPUT_STYLE_SIMPLIFY:
            for (int j=0; j<equal_vars.size(); j++) {
              VarInfo other = (VarInfo) equal_vars.elementAt(j);
	      if (other.isDerivedSequenceMinMaxSum())
		break;
              if (vi.rep_type.isArray()) {
		String[] form =
		  VarInfoName.QuantHelper.format_simplify(new VarInfoName[]
		    { vi.name, other.name }, true); // elementwise
                out.println(form[0] + "(EQ " + form[1] + " " + form[2] + " )" + form[3]);
              } else {
		out.println("(EQ " + vi.name.simplify_name() +
			    " " + other.name.simplify_name() + ")");
	      }
            }
	    break;
	  case Daikon.OUTPUT_STYLE_IOA:
            sb = new StringBuffer();
            for (int j=0; j<equal_vars.size(); j++) {
              VarInfo other = (VarInfo) equal_vars.elementAt(j);
	      if (j>0) sb.append("\n");
	      sb.append("invariant of " + classname + ": ");
	      sb.append(vi.name.ioa_name(classname) + " = ");
	      sb.append(other.name.ioa_name(classname));
	    }
	    out.println(sb.toString());
	    break;
	  default:
	    throw new IllegalStateException("Unknown output mode");
          }
        }
      }
    }

    // I could instead sort the PptSlice objects, then sort the invariants
    // in each PptSlice.  That would be more efficient, but this is
    // probably not a bottleneck anyway.
    Vector invs_vector = invariants_vector();
    Invariant[] invs_array = (Invariant[]) invs_vector.toArray(new Invariant[invs_vector.size()]);
    Arrays.sort(invs_array, icfp);

    Global.non_falsified_invariants += invs_array.length;
    for (int ia_index = 0; ia_index<invs_array.length; ia_index++) {
      Invariant inv = invs_array[ia_index];
      int num_vals = inv.ppt.num_values();
      int inv_num_samps = inv.ppt.num_samples();
      String num_values_samples = "\t\t(" +
	nplural(num_vals, "value") + ", " +
        nplural(inv_num_samps, "sample") + ")";

      // I could imagine printing information about the PptSlice
      // if it has changed since the last Invariant I examined.
      PptSlice slice = inv.ppt;
      if (Invariant.debugPrint.isDebugEnabled()) {
        Invariant.debugPrint.debug("Slice: " + slice.varNames() + "  "
				   + slice.num_samples() + " samples");
        Invariant.debugPrint.debug("    Samples breakdown: "
				   + slice.tuplemod_samples_summary());
        // slice.values_cache.dump();
      }
      Assert.assert(slice.check_modbits());

      // isWorthPrinting checks many conditions for suppression
      if (! inv.isWorthPrinting()) {
	// out.println("Not worth printing: " + inv.format() + ", " + inv.repr());
	continue;
      }

      // Redundancy is separate from worth printing for now, but it
      // probably should not be, in general.
      if (Daikon.suppress_redundant_invariants_with_simplify &&
	  ((PptTopLevel) inv.ppt.parent).redundant_invs.contains(inv)) {
	daikon.simplify.SessionManager.debugln("Redundant: " + inv.format());
	continue;
      }

      if (Daikon.output_style != Daikon.OUTPUT_STYLE_NORMAL ||
	  Daikon.output_style != Daikon.OUTPUT_STYLE_IOA) {
	// don't print out invariants with min(), max(), or sum() variables
	boolean mms = false;
	VarInfo[] varbls = inv.ppt.var_infos;
	for (int v=0; !mms && v<varbls.length; v++) {
	  mms |= varbls[v].isDerivedSequenceMinMaxSum();
	}
	if (mms) { continue; }
      }

      String inv_rep;
      switch (Daikon.output_style) {
      case Daikon.OUTPUT_STYLE_NORMAL:
	inv_rep = inv.format();
	break;
      case Daikon.OUTPUT_STYLE_ESC:
	inv_rep = inv.format_esc();
	break;
      case Daikon.OUTPUT_STYLE_SIMPLIFY:
	inv_rep = inv.format_simplify();
	break;
      case Daikon.OUTPUT_STYLE_IOA:
	inv_rep = "invariant of " + classname + ": ";
	inv_rep += inv.format_ioa(classname);
	inv_rep += "\n" + inv.repr();
	break;
      default:
	throw new IllegalStateException("Unknown output mode");
      }
      if (Daikon.output_num_samples) {
        inv_rep += num_values_samples;
      }
      out.println(inv_rep);
      if (Invariant.debugPrint.isDebugEnabled()) {
        Invariant.debugPrint.debug("  [" + inv.repr_prob() + "]");
      }
      Global.reported_invariants++;

//    {
//  	// Print out any subexpressions of this which are
//  	// non-canonical (and thus could be replaced with a canonical
//  	// form).  There are never any of these, though, since we
//  	// don't derive from non-canonical variables.
//  	Iterator iter = Arrays.asList(inv.ppt.var_infos).iterator();
//  	while (iter.hasNext()) {
//  	  VarInfoName name = ((VarInfo) iter.next()).name;
//  	  Iterator nodes = (new VarInfoName.InorderFlattener(name)).nodes().iterator();
//  	  nodes.next(); // skip the root
//  	  while (nodes.hasNext()) {
//  	    VarInfoName node = (VarInfoName) nodes.next();
//  	    VarInfo info = findVar(node);
//  	    if ((info != null) && !info.isCanonical()) {
//  	      out.println("** sub node not canonical: " + node);
//  	    }
//  	  }
//  	}
//    }
    }
  }


  // /** Print invariants for a single program point. */
  // public void print_invariants() {
  //   System.out.println(name + "  "
  //                      + num_samples() + " samples");
  //   System.out.println("    Samples breakdown: "
  //                      + values.tuplemod_samples_summary());
  //   // for (Iterator itor2 = views.keySet().iterator() ; itor2.hasNext() ; ) {
  //   for (Iterator itor2 = views.iterator() ; itor2.hasNext() ; ) {
  //     PptSlice slice = (PptSlice) itor2.next();
  //     if (Invariant.debugPrint.isDebugEnabled()) {
  //       System.out.println("Slice: " + slice.varNames() + "  "
  //                          + slice.num_samples() + " samples");
  //       System.out.println("    Samples breakdown: "
  //                          + slice.values_cache.tuplemod_samples_summary());
  //     }  //     Invariants invs = slice.invs;
  //     int num_invs = invs.size();
  //     for (int i=0; i<num_invs; i++) {
  //       Invariant inv = invs.elementAt(i);
  //       String inv_rep = inv.format();
  //       if (inv_rep != null) {
  //         System.out.println(inv_rep);
  //         if (Invariant.debugPrint.isDebugEnabled()) {
  //           System.out.println("  " + inv.repr());
  //         }
  //       } else {
  //         if (Invariant.debugPrint.isDebugEnabled()) {
  //           System.out.println("[suppressed: " + inv.repr() + " ]");
  //         }
  //       }
  //     }
  //   }
  // }

  static Comparator arityVarnameComparator = new PptSlice.ArityVarnameComparator();

}
