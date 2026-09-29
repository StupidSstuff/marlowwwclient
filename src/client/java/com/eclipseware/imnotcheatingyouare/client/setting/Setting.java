package com.eclipseware.imnotcheatingyouare.client.setting;

import com.eclipseware.imnotcheatingyouare.client.module.Module;
import java.util.ArrayList;

public class Setting {
    private String name;
    private Module parent;
    private String mode;
    private String sval;
    private ArrayList<String> options;
    private boolean bval;
    private double dval;
    private double min;
    private double max;
    private boolean onlyint = false;

    private String textVal = "";

    public Setting(String name, Module parent, String sval, ArrayList<String> options){
        this.name = name; this.parent = parent; this.sval = sval; this.options = options; this.mode = "Combo";
    }
    
    public Setting(String name, Module parent, boolean bval){
        this.name = name; this.parent = parent; this.bval = bval; this.mode = "Check";
    }
    
    public Setting(String name, Module parent, double dval, double min, double max, boolean onlyint){
        this.name = name; this.parent = parent; this.min = min; this.max = max; this.onlyint = onlyint; this.mode = "Slider";
        this.dval = clamp(dval);
    }

    public Setting(String name, Module parent, String defaultText, boolean isText) {
        this.name = name; this.parent = parent; this.textVal = defaultText; this.mode = "Text";
    }

    private int colorVal = 0xFFFFFFFF;
    public Setting(String name, Module parent, java.awt.Color defaultColor) {
        this.name = name; this.parent = parent; this.colorVal = defaultColor.getRGB(); this.mode = "Color";
    }
    
    private double dval2;
    private double[] curve = {0.25, 0.1, 0.25, 1.0};
    private double[] curveDefault = {0.25, 0.1, 0.25, 1.0};

    public Setting(String name, Module parent, double x1, double y1, double x2, double y2) {
        this.name = name; this.parent = parent; this.mode = "Curve";
        this.curve = new double[]{x1, y1, x2, y2};
        this.curveDefault = new double[]{x1, y1, x2, y2};
    }

    public boolean isCurve() { return this.mode.equalsIgnoreCase("Curve"); }
    public double[] getCurve() { return this.curve; }
    public double[] getCurveDefault() { return this.curveDefault; }
    public void setCurve(double x1, double y1, double x2, double y2) {
        this.curve = new double[]{Math.max(0.0, Math.min(1.0, x1)), Math.max(-0.25, Math.min(1.5, y1)),
                Math.max(0.0, Math.min(1.0, x2)), Math.max(-0.25, Math.min(1.5, y2))};
    }
    public Setting(String name, Module parent, double lo, double hi, double min, double max, boolean onlyint) {
        this.name = name; this.parent = parent; this.min = min; this.max = max; this.onlyint = onlyint; this.mode = "Range";
        double a = clamp(lo), b = clamp(hi);
        this.dval = Math.min(a, b);
        this.dval2 = Math.max(a, b);
    }

    private java.util.function.BooleanSupplier visibleWhen = null;
    public Setting visibleWhen(java.util.function.BooleanSupplier supplier) { this.visibleWhen = supplier; return this; }
    public boolean isVisible() { return visibleWhen == null || visibleWhen.getAsBoolean(); }

    public boolean isRange() { return this.mode.equalsIgnoreCase("Range"); }
    public double getRangeLow() { return this.onlyint ? (int) dval : dval; }
    public double getRangeHigh() { return this.onlyint ? (int) dval2 : dval2; }
    public void setRange(double lo, double hi) {
        double a = clamp(lo), b = clamp(hi);
        this.dval = Math.min(a, b);
        this.dval2 = Math.max(a, b);
    }

    public String getName() { return name; }
    public Module getParentMod() { return parent; }
    public String getValString() { return this.sval; }
    public void setValString(String in) { this.sval = in; }
    public ArrayList<String> getOptions() { return this.options; }
    public boolean getValBoolean() { return this.bval; }
    public void setValBoolean(boolean in) { this.bval = in; }
    public double getValDouble(){ return this.onlyint ? (int)dval : this.dval; }
    public void setValDouble(double in) { this.dval = clamp(in); }
    public void setValDoubleUnclamped(double in) { this.dval = in; }
    public double getMin() { return this.min; }
    public double getMax() { return this.max; }
    public boolean isCombo() { return this.mode.equalsIgnoreCase("Combo"); }
    public boolean isCheck() { return this.mode.equalsIgnoreCase("Check"); }
    public boolean isSlider() { return this.mode.equalsIgnoreCase("Slider"); }
    public boolean isText() { return this.mode.equalsIgnoreCase("Text"); }
    public boolean isColor() { return this.mode.equalsIgnoreCase("Color"); }
    public String getValText() { return this.textVal; }
    public void setValText(String in) { this.textVal = in; }
    public int getValColor() { return this.colorVal; }
    public void setValColor(int in) { this.colorVal = in; }
    public boolean onlyInt() { return this.onlyint; }

    private double clamp(double value) {
        return Math.max(this.min, Math.min(this.max, value));
    }
}