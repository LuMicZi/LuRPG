package com.lumiczi.lurpg.class_;

/**
 * Stores the base attributes and per-level growth values for a GameClass.
 * <p>
 * Base attributes represent the stat values at level 1.
 * Growth attributes represent the increment per level above 1.
 * </p>
 * <p>
 * The {@link #getStatAtLevel(int)} method computes the effective attribute
 * values at a given level using the formula: {@code base + growth * (level - 1)}.
 * </p>
 */
public class ClassAttribute {

    // Base stats (at level 1)
    private double maxHealth;
    private double physicalAttack;
    private double magicalAttack;
    private double physicalDefense;
    private double magicalDefense;
    private double resourceMax;

    // Growth per level
    private double healthGrowth;
    private double pAtkGrowth;
    private double mAtkGrowth;
    private double pDefGrowth;
    private double mDefGrowth;
    private double resourceGrowth;

    public ClassAttribute() {
    }

    public ClassAttribute(double maxHealth, double physicalAttack, double magicalAttack,
                          double physicalDefense, double magicalDefense, double resourceMax) {
        this.maxHealth = maxHealth;
        this.physicalAttack = physicalAttack;
        this.magicalAttack = magicalAttack;
        this.physicalDefense = physicalDefense;
        this.magicalDefense = magicalDefense;
        this.resourceMax = resourceMax;
    }

    /**
     * Returns a new ClassAttribute containing only the base stats (growth values set to 0).
     *
     * @return a new ClassAttribute with base values only
     */
    public ClassAttribute getBase() {
        ClassAttribute attr = new ClassAttribute();
        attr.maxHealth = this.maxHealth;
        attr.physicalAttack = this.physicalAttack;
        attr.magicalAttack = this.magicalAttack;
        attr.physicalDefense = this.physicalDefense;
        attr.magicalDefense = this.magicalDefense;
        attr.resourceMax = this.resourceMax;
        return attr;
    }

    /**
     * Returns a new ClassAttribute containing only the growth stats (base values set to 0).
     *
     * @return a new ClassAttribute with growth values only
     */
    public ClassAttribute getGrowth() {
        ClassAttribute attr = new ClassAttribute();
        attr.healthGrowth = this.healthGrowth;
        attr.pAtkGrowth = this.pAtkGrowth;
        attr.mAtkGrowth = this.mAtkGrowth;
        attr.pDefGrowth = this.pDefGrowth;
        attr.mDefGrowth = this.mDefGrowth;
        attr.resourceGrowth = this.resourceGrowth;
        return attr;
    }

    /**
     * Calculates the effective attribute values at the given level.
     * Uses the formula: {@code base + growth * (level - 1)}.
     * The returned object also retains the growth values for reference.
     *
     * @param level the level to calculate stats for (minimum 1)
     * @return a new ClassAttribute with computed values
     */
    public ClassAttribute getStatAtLevel(int level) {
        int levelsAboveBase = Math.max(0, level - 1);
        ClassAttribute attr = new ClassAttribute();
        attr.maxHealth = this.maxHealth + this.healthGrowth * levelsAboveBase;
        attr.physicalAttack = this.physicalAttack + this.pAtkGrowth * levelsAboveBase;
        attr.magicalAttack = this.magicalAttack + this.mAtkGrowth * levelsAboveBase;
        attr.physicalDefense = this.physicalDefense + this.pDefGrowth * levelsAboveBase;
        attr.magicalDefense = this.magicalDefense + this.mDefGrowth * levelsAboveBase;
        attr.resourceMax = this.resourceMax + this.resourceGrowth * levelsAboveBase;
        // Retain growth values
        attr.healthGrowth = this.healthGrowth;
        attr.pAtkGrowth = this.pAtkGrowth;
        attr.mAtkGrowth = this.mAtkGrowth;
        attr.pDefGrowth = this.pDefGrowth;
        attr.mDefGrowth = this.mDefGrowth;
        attr.resourceGrowth = this.resourceGrowth;
        return attr;
    }

    // --- Getters and Setters ---

    public double getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
    }

    public double getPhysicalAttack() {
        return physicalAttack;
    }

    public void setPhysicalAttack(double physicalAttack) {
        this.physicalAttack = physicalAttack;
    }

    public double getMagicalAttack() {
        return magicalAttack;
    }

    public void setMagicalAttack(double magicalAttack) {
        this.magicalAttack = magicalAttack;
    }

    public double getPhysicalDefense() {
        return physicalDefense;
    }

    public void setPhysicalDefense(double physicalDefense) {
        this.physicalDefense = physicalDefense;
    }

    public double getMagicalDefense() {
        return magicalDefense;
    }

    public void setMagicalDefense(double magicalDefense) {
        this.magicalDefense = magicalDefense;
    }

    public double getResourceMax() {
        return resourceMax;
    }

    public void setResourceMax(double resourceMax) {
        this.resourceMax = resourceMax;
    }

    public double getHealthGrowth() {
        return healthGrowth;
    }

    public void setHealthGrowth(double healthGrowth) {
        this.healthGrowth = healthGrowth;
    }

    public double getPatkGrowth() {
        return pAtkGrowth;
    }

    public void setPatkGrowth(double pAtkGrowth) {
        this.pAtkGrowth = pAtkGrowth;
    }

    public double getMatkGrowth() {
        return mAtkGrowth;
    }

    public void setMatkGrowth(double mAtkGrowth) {
        this.mAtkGrowth = mAtkGrowth;
    }

    public double getPdefGrowth() {
        return pDefGrowth;
    }

    public void setPdefGrowth(double pDefGrowth) {
        this.pDefGrowth = pDefGrowth;
    }

    public double getMdefGrowth() {
        return mDefGrowth;
    }

    public void setMdefGrowth(double mDefGrowth) {
        this.mDefGrowth = mDefGrowth;
    }

    public double getResourceGrowth() {
        return resourceGrowth;
    }

    public void setResourceGrowth(double resourceGrowth) {
        this.resourceGrowth = resourceGrowth;
    }

    @Override
    public String toString() {
        return "ClassAttribute{" +
                "maxHealth=" + maxHealth +
                ", physicalAttack=" + physicalAttack +
                ", magicalAttack=" + magicalAttack +
                ", physicalDefense=" + physicalDefense +
                ", magicalDefense=" + magicalDefense +
                ", resourceMax=" + resourceMax +
                ", healthGrowth=" + healthGrowth +
                ", pAtkGrowth=" + pAtkGrowth +
                ", mAtkGrowth=" + mAtkGrowth +
                ", pDefGrowth=" + pDefGrowth +
                ", mDefGrowth=" + mDefGrowth +
                ", resourceGrowth=" + resourceGrowth +
                '}';
    }
}
