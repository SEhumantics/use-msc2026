/*
 * Vendored from the uDataTypes library of the USE-Uncertainty project.
 *
 *   upstream: https://github.com/atenearesearchgroup/uncertainty
 *   commit:   74acd0d
 *   path:     uDataTypes/Libraries/Java/src/uDataTypes/UInteger.java
 *   licence:  GNU General Public License v2 (the upstream COPYING), the same licence
 *             as USE itself, so vendoring is licence-clean.
 *
 * Three deliberate changes from upstream, all recorded in
 * docs/port2/stage-03-scope.md or this task's work log:
 *
 *   1. RELOCATED from package `uDataTypes` to `org.tzi.use.uncertainty.datatypes`
 *      (B1). The historical jar used by the differential harness still carries the
 *      original package, which is what keeps the two sides distinguishable.
 *
 *   2. PURGED of the UUnlimitedNatural conversions (sec. 5). `UUnlimitedNatural` is a
 *      complete 476-line class upstream but was never bound to the OCL language --
 *      zero occurrences in USE-Uncertainty/src, and the grammar admits exactly five
 *      uncertain type names. It was in the compile closure only because UReal and
 *      UInteger declared conversions to it. Removing those drops it entirely.
 *      No differential row can reach a removed member: `toUUnlimitedNatural` is
 *      registered nowhere under expr/operations.
 *
 *   3. DROPPED the `Cloneable` declaration. The upstream `clone()` override
 *      (uDataTypes/UInteger.java:544) had zero callers anywhere in USE-Uncertainty/src and was
 *      correctly not ported (sec. 5 policy: delete if dangling). The class still declared
 *      `implements Cloneable` after that drop, which is a broken contract (Object.clone() is
 *      protected, so external code could never actually call it) rather than a real capability;
 *      removing the declaration is the honest fix. Found and fixed during the 2026-08-21
 *      completeness review.
 *
 * Otherwise byte-for-byte upstream. Do not reformat: the port's auditability depends
 * on this file staying diffable against its origin.
 */
package org.tzi.use.uncertainty.datatypes;

public class UInteger implements Comparable<UInteger> {
	
	protected int x = 0; 
	protected double u = 0.0;

    /**
     * Constructors 
     */
    public UInteger () {
        this.x = 0; this.u = 0.0;
    }

	public UInteger(int x){ //"promotes" a real x to (x,0) 
		this.x = x; this.u = 0.0;
	}
  
    public UInteger (int x, double u) {
        this.x = x; this.u = Math.abs(u);
    }
	
    public UInteger(String x) { //creates an UReal from a string representing a real, with u=0.
    	this.x = Integer.parseInt(x);
    	this.u = 0.0;
    }
    
    public UInteger(String x, String u) { //creates an UReal from two strings representing (x,u).
    	this.x = Integer.parseInt(x);
    	this.u = Math.abs(Double.parseDouble(u));
    }
   
    /**
     * Setters and getters 
     */
    public int getX() {
		return x; 
	}
    public void setX(int x) {
		this.x = x; 
	}
    public double getU() {
		return u;
	}
	public void setU(double u) {
		this.u = Math.abs(u);
	}

   /*********
     * 
     * Type Operations
     */

	
	public UInteger add(UInteger r) {
		UInteger result = new UInteger();
		result.setX(this.getX() + r.getX());
		result.setU( Math.sqrt((this.getU() * this.getU()) + (r.getU() * r.getU()) ));
		return result;
	}
	

	public UInteger minus(UInteger r) {
		UInteger result = new UInteger();
			result.setX(this.getX() - r.getX());
			if (r==this) result.setU(0.0); // pathological case, x-x
			else result.setU(Math.sqrt((this.getU()*this.getU()) + (r.getU()*r.getU())));
			return result;
	}

	
	public UInteger mult(UInteger r) {
		UInteger result = new UInteger();
		
		result.setX(this.getX() * r.getX());
		
		double a = r.getX()*r.getX()*this.getU()*this.getU();
		double b = this.getX()*this.getX()*r.getU()*r.getU();
		result.setU(Math.sqrt(a + b));
		return result;
	}
	
	
	public UInteger divideBy(UInteger r) {
		UInteger result = new UInteger();
	
		if (r==this) { // pathological cases x/x
			result.setX(1);
			result.setU(0.0);
			return result;
		}
		if (r.getU()==0.0) { // r is a scalar
			result.setX(this.getX() / r.getX());
			result.setU(this.getU() / r.getX()); // "this" may be a scalar, too
			return result;
		}
		if (this.getU()==0.0) { // "this is a scalar, r is not
			result.setX(this.getX() / r.getX());
			result.setU(r.getU()/(r.getX()*r.getX()));
			return result;
		}
		// both variables have associated uncertainty
		
		double a = this.getX() / r.getX();
		double b = 0.0; //(this.getX()*r.getU()*r.getU())/(r.getX()*r.getX()*r.getX());
		result.setX((int)Math.floor(a + b));
		
		double c = Math.abs(((this.getU()*this.getU())/r.getX()));
		double d = (this.getX()*this.getX()*r.getU()*r.getU()) / (r.getX()*r.getX()*r.getX()*r.getX());
		result.setU(Math.sqrt(c + d));
		
		return result;
	}
	
	/** this operation returns a UReal
	 */
	public UReal divideByR(UInteger r) {
		UReal result = new UReal();
	
		if (r==this) { // pathological cases x/x
			result.setX(1.0);
			result.setU(0.0);
			return result;
		}
		if (r.getU()==0.0) { // r is a scalar
			result.setX((double)this.getX() / (double)r.getX());
			result.setU(this.getU() / (double)r.getX()); // "this" may be a scalar, too
			return result;
		}
		if (this.getU()==0.0) { // "this is a scalar, r is not
			result.setX((double)this.getX() / (double)r.getX());
			result.setU(r.getU()/(r.getX()*r.getX()));
			return result;
		}
		// both variables have associated uncertainty
		double a = (double)this.getX() / (double)r.getX();
//		double b = (this.getX()*r.getU()*r.getU())/(Math.pow(r.getX(), 3));
		double b = 0.0; // (this.getX()*r.getU()*r.getU())/(r.getX()*r.getX()*r.getX());
		result.setX(a + b);
		
		double c = Math.abs(((this.getU()*this.getU())/r.getX()));
//		double d = (this.getX()*this.getX()*r.getU()*r.getU()) / Math.pow(r.getX(), 4);
		double d = (this.getX()*this.getX()*r.getU()*r.getU()) / (r.getX()*r.getX()*r.getX()*r.getX());
		result.setU(Math.sqrt(c + d));
		
		return result;
	}
	
	public UInteger mod(UInteger r) {
		UInteger result = new UInteger();
	
		if (r==this) { // pathological cases x/x
			result.setX(0);
			result.setU(0.0);
			return result;
		}
		if (r.getU()==0.0) { // r is a scalar
			result.setX(this.getX() % r.getX());
			result.setU(this.getU() / r.getX()); // "this" may be a scalar, too
			return result;
		}
		if (this.getU()==0.0) { // "this is a scalar, r is not
			result.setX(this.getX() % r.getX());
			result.setU(r.getU()/(r.getX()*r.getX()));
			return result;
		}
		// both variables have associated uncertainty
		
		double a = this.getX() % r.getX();
		double b = 0.0; //(this.getX()*r.getU()*r.getU())/(r.getX()*r.getX()*r.getX());
		result.setX((int)Math.floor(a + b));
		
		double c = Math.abs(((this.getU()*this.getU())/r.getX()));
		double d = (this.getX()*this.getX()*r.getU()*r.getU()) / (r.getX()*r.getX()*r.getX()*r.getX());
		result.setU(Math.sqrt(c + d));
		
		return result;
	}

	/***
	 * Rest of the type operations
	 * 
	 */
	
	public UInteger abs() {
		UInteger result = new UInteger();
	
		result.setX(Math.abs(this.getX()));
		result.setU(this.getU());
	
		return result;
	}
	
	
	public UInteger neg() {
		UInteger result = new UInteger();
		
		result.setX(-this.getX());
		result.setU(this.getU());
	
		return result;
	}

	
	public UInteger power(float s) {
		return this.toUReal().power(s).toUInteger();
 	}

	
	public UInteger sqrt() {
		return this.toUReal().sqrt().toUInteger();
	}

	public UInteger inverse() { //inverse (reciprocal)
		return new UInteger(1,0.0).divideBy(this);
	}
	

	/***
	 *   FUZZY COMPARISON OPERATIONS
	 *   Assume UReal values (x,u) represent standard uncertainty values, i.e., they follow a Normal distribution
	 *   of mean x and standard deviation \sigma = u
	 */


	public UBoolean equals(UInteger number) {
		return this.toUReal().uEquals(number.toUReal());
	}

	public UBoolean lt(UInteger number) {
		return this.toUReal().lt(number.toUReal());
	}
	
	public UBoolean le(UInteger number) {
		return this.toUReal().le(number.toUReal());
	}

	public UBoolean gt(UInteger number) {
		return this.toUReal().gt(number.toUReal());
	}

	
	public UBoolean ge(UInteger number) {
		return this.toUReal().ge(number.toUReal());
	}
   
	/*** 
	 *   END OF FUZZY COMPARISON OPERATIONS
	 */


	@Override
	public int compareTo(UInteger other) {
		if (this.equals(other).toBoolean()) return 0;
		if (this.lt(other).toBoolean()) return -1;
		return 1;
	}

	public UInteger min(UInteger r) {
		if (r.lt(this).toBoolean()) return new UInteger(r.getX(),r.getU()); 
		return new UInteger(this.getX(),this.getU());
	}
	public UInteger max(UInteger r) {
		//if (r>this) r; else this;
		if (r.gt(this).toBoolean()) return new UInteger(r.getX(),r.getU());
		return new UInteger(this.getX(),this.getU());
	}

	/******
	 * Conversions
	 */
	
	public String toString() {
		//return "(" + x + "," + u + ")";
		return String.format("UInteger(%d, %5.3f)", this.getX(), this.getU());
	}
	
	
	public int toInteger(){ //
		return this.getX();
	}
	
	public double toReal()  { 
		return this.getX();
	}
	
	public UReal toUReal() {
		return new UReal(this.getX(),this.getU());
	}

	// PURGED: toUUnlimitedNatural() -- see the file header. UUnlimitedNatural is not bound
	// to the OCL language in the fork and is registered as no operation, so nothing can reach it.


}
