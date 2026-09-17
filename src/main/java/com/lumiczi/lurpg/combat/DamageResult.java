package com.lumiczi.lurpg.combat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the result of a damage calculation in the RPG combat system.
 * <p>
 * Contains the breakdown of physical damage, magical damage, elemental damage,
 * critical hit information, lifesteal amount, and the final total damage.
 * </p>
 */
public class DamageResult {

    private double physicalDamage;
    private double magicalDamage;
    private Map<String, Double> elementDamage;
    private boolean isCrit;
    private double lifestealAmount;
    private double finalDamage;

    public DamageResult() {
        this.physicalDamage = 0;
        this.magicalDamage = 0;
        this.elementDamage = new HashMap<>();
        this.isCrit = false;
        this.lifestealAmount = 0;
        this.finalDamage = 0;
    }

    /**
     * Returns the total damage from all sources (physical + magical + elemental).
     *
     * @return the total damage
     */
    public double getTotalDamage() {
        double elementTotal = 0;
        for (Double dmg : elementDamage.values()) {
            elementTotal += dmg;
        }
        return physicalDamage + magicalDamage + elementTotal;
    }

    /**
     * Returns the elemental damage for the specified element.
     *
     * @param element the element name (e.g. "fire", "ice", "thunder", "dark", "light")
     * @return the elemental damage, or 0 if not present
     */
    public double getElementDamage(String element) {
        return elementDamage.getOrDefault(element, 0.0);
    }

    /**
     * Returns an unmodifiable view of all elemental damage entries.
     *
     * @return unmodifiable map of element name to damage
     */
    public Map<String, Double> getElementDamageMap() {
        return Collections.unmodifiableMap(elementDamage);
    }

    // --- Getters and Setters ---

    public double getPhysicalDamage() {
        return physicalDamage;
    }

    public void setPhysicalDamage(double physicalDamage) {
        this.physicalDamage = physicalDamage;
    }

    public double getMagicalDamage() {
        return magicalDamage;
    }

    public void setMagicalDamage(double magicalDamage) {
        this.magicalDamage = magicalDamage;
    }

    public Map<String, Double> getElementDamage() {
        return elementDamage;
    }

    public void setElementDamage(Map<String, Double> elementDamage) {
        this.elementDamage = elementDamage != null ? elementDamage : new HashMap<>();
    }

    public boolean isCrit() {
        return isCrit;
    }

    public void setCrit(boolean crit) {
        isCrit = crit;
    }

    public double getLifestealAmount() {
        return lifestealAmount;
    }

    public void setLifestealAmount(double lifestealAmount) {
        this.lifestealAmount = lifestealAmount;
    }

    public double getFinalDamage() {
        return finalDamage;
    }

    public void setFinalDamage(double finalDamage) {
        this.finalDamage = finalDamage;
    }

    @Override
    public String toString() {
        return "DamageResult{" +
                "physicalDamage=" + physicalDamage +
                ", magicalDamage=" + magicalDamage +
                ", elementDamage=" + elementDamage +
                ", isCrit=" + isCrit +
                ", lifestealAmount=" + lifestealAmount +
                ", finalDamage=" + finalDamage +
                '}';
    }
}
