package daikon;

import daikon.inv.*;

import utilMDE.*;

// This is a view on the full data (and maybe it does cacheing for a
// while).  This will be efficient for iteration (albeit with repetition),
// but inefficient for lookup (because we'd have to iterate over all
// entries -- or at least all keys -- to find any such instance, but we
// need to find them all to give a good result).

public abstract class PptSlice extends Ppt {
  public Ppt parent;
  public int arity;
  // var_infos appears in Ppt; don't repeat it here!!
  // public VarInfo[] var_infos;
  // cache of values from var_infos, to avoid repeated lookups
  public int[] value_indices;

  // Put off implementing this for the nonce.  It should probably
  // appear in the concrete children, anyway.
  // This is a local cache.
  // HashMap values_cache;		// map from ValueTuple to Integer (a count)

  public Invariants invs;

  PptSlice(Ppt parent_, VarInfo[] var_infos_) {
    super();
    parent = parent_;
    var_infos = var_infos_;
    // System.out.println("in PptSlice(): this=" + this);
    // System.out.println("var_infos = " + var_infos + " = " + this.var_infos);
    name = parent.name + varNames();
    arity = var_infos.length;
    value_indices = new int[arity];
    for (int i=0; i<arity; i++)
      value_indices[i] = var_infos[i].value_index;
    // values_cache = new HashMap();
    invs = new Invariants();
    parent.addView(this);
  }

  public boolean usesVar(VarInfo vi) {
    return (ArraysMDE.indexOfEq(var_infos, vi) != 0);
  }

}

/// These functions create real new Ppt objects as oppose to slices.

// def dict_of_tuples_slice_2(dot, i1, i2):
//     """Input: a dictionary mapping a tuple of elements to a count, and a
//     list of indices.
//     Output: a dictionary mapping a subset of the original elements to a count.
//     The subset is chosen according to the input indices."""
//
//     result = {}
//     for (key_tuple, count) in dot.items():
//         # sliced_tuple = util.slice_by_sequence(key_tuple, indices)
//         sliced_tuple = (key_tuple[i1][0], key_tuple[i2][0])
//         modified = key_tuple[i1][1] and key_tuple[i2][1]
//         this_counts = result.get(sliced_tuple, [0, 0])
//         result[sliced_tuple] = this_counts
//         this_counts[0] = this_counts[0] + count
//         if modified:
//             this_counts[1] = this_counts[1] + count
//     return result
//
// def dict_of_tuples_slice_3(dot, i1, i2, i3):
//     """Input: a dictionary mapping a tuple of elements to a count, and a
//     list of indices.
//     Output: a dictionary mapping a subset of the original elements to a count.
//     The subset is chosen according to the input indices."""
//
//     result = {}
//     for (key_tuple, count) in dot.items():
//         # sliced_tuple = util.slice_by_sequence(key_tuple, indices)
//         sliced_tuple = (key_tuple[i1][0], key_tuple[i2][0], key_tuple[i3][0])
//         modified = key_tuple[i1][1] and key_tuple[i2][1] and key_tuple[i3][1]
//         this_counts = result.get(sliced_tuple, [0, 0])
//         result[sliced_tuple] = this_counts
//         this_counts[0] = this_counts[0] + count
//         if modified:
//             this_counts[1] = this_counts[1] + count
//     return result
