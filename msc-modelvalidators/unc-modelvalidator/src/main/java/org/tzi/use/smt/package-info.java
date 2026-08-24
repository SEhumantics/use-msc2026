/**
 * SMT-based bounded model finding for USE/OCL models with crisp and uncertainty-typed attributes.
 *
 * <p>All solver interaction is portable SMT-LIB 2.6 text executed through an external process; no
 * solver-specific Java API is used anywhere in this module. See
 * {@code output/THESIS_SMT_MODEL_FINDER_PLAN.md} section 1.3 for why.
 */
package org.tzi.use.smt;
