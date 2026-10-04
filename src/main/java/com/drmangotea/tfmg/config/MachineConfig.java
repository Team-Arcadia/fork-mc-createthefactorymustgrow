package com.drmangotea.tfmg.config;


import net.createmod.catnip.config.ConfigBase;

public class MachineConfig extends ConfigBase {

    public final ConfigInt fireExtinguisherClearRadius = i(1, 0, "fireExtinguisherClearRadius", Comments.fireExtinguisherClearRadius);

    public final ConfigFloat electricMotorInternalResistance = f(30, 0, "electricMotorInternalResistance", Comments.electricMotorInternalResistance);
    public final ConfigInt cokeOvenMaxSize = i(5, 1, "cokeOvenMaxSize", Comments.cokeOvenMaxSize);
    // Minimum is positive because this value is a DIVISOR in the accumulator and
    // converter energy paths. At 0 the float division yields Infinity and the
    // cast to int lands on Integer.MAX_VALUE, so a single tick filled or drained
    // the whole storage and the FE conversion silently stopped making sense.
    // Kept fractional-friendly rather than clamped to 1 so rates below one FE
    // per watt-tick remain configurable.
    public final ConfigFloat FEtoWattTickConversionRate = f(1, 0.001f, "FEtoWattTickConversionRate", Comments.FEtoWattTickConversionRate);


    public final ConfigInt electrolysisMinimumCurrent = i(5, 1, "electrolysisMinimumCurrent", Comments.electrolysisMinimumCurrent);
    public final ConfigInt engineMaxLength = i(5, 1, "engineMaxLength", Comments.engineMaxLength);
    public final ConfigInt surfaceScannerScanDepth = i(-64, -512, "surfaceScannerScanDepth", Comments.surfaceScannerScanDepth);
    public final ConfigInt polarizerItemChargingRate = i(1000, 1, "polarizerItemChargingRate", Comments.polarizerItemChargingRate);


    public final ConfigGroup accumulator = group(1, "accumulator", "Accumulator");
    public final ConfigInt accumulatorStorage = i(100000, 1, "accumulatorStorage", Comments.accumulatorStorage);
    public final ConfigInt accumulatorVoltage = i(12, 1, "accumulatorVoltage", Comments.accumulatorVoltage);
    public final ConfigInt accumulatorMaxAmpOutput = i(20, 1, "accumulatorMaxAmpOutput", Comments.accumulatorMaxAmpOutput);
    public final ConfigInt accumulatorChargingRate = i(100, 1, "accumulatorChargingRate", Comments.accumulatorChargingRate);

    public final ConfigGroup firebox = group(1, "firebox", "Firebox");
    public final ConfigBool fireboxExhaustRequirement = b(true, "fireboxExhaustRequirement", Comments.fireboxExhaustRequirement);
    public final ConfigInt fireboxFuelConsumption = i(100, 1, "fireboxFuelConsumption", Comments.fireboxFuelConsumption);

    public final ConfigGroup engines = group(1, "engines", "Engines");
    public final ConfigFloat engineLoudness = f(1,0, "engineLoudness", Comments.engineLoudness);


    public final ConfigGroup generators = group(1, "generators", "Generators");
    public final ConfigFloat largeGeneratorModifier = f(4, 0, "largeGeneratorModifier", Comments.largeGenerator);
    public final ConfigFloat largeGeneratorMinSpeed = f(70, 0, "largeGeneratorMinSpeed", Comments.largeGeneratorMinSpeed);
    public final ConfigFloat largeGeneratorVoltageMultiplier = f(3, 0, "largeGeneratorVoltageMultiplier", Comments.largeGeneratorVoltageMultiplier);
    public final ConfigFloat largeGeneratorPowerMultiplier = f(2, 0, "largeGeneratorPowerMultiplier", Comments.largeGeneratorPowerMultiplier);
    public final ConfigInt largeGeneratorMaxVoltage = i(10000, 1, "largeGeneratorMaxVoltage", Comments.largeGeneratorMaxVoltage);
    //
    public final ConfigFloat generatorModifier = f(1.4f, 0, "GeneratorModifier", Comments.generator);
    public final ConfigFloat generatorMinSpeed = f(40, 0, "generatorMinSpeed", Comments.generatorMinSpeed);
    public final ConfigInt generatorMaxVoltage = i(1000, 1, "generatorMaxVoltage", Comments.generatorMaxVoltage);

    public final ConfigGroup blast_furnace = group(1, "blast_furnace", "Blast Furnace");
    public final ConfigInt blastFurnaceMaxHeight = i(10, 3, "blastFurnaceMaxHeight", Comments.blastFurnaceHeight);
    public final ConfigFloat blastFurnaceHeightSpeedModifier = f(1f, 0.1f, "blastFurnaceHeightSpeedModifier", Comments.blastFurnaceHeightSpeedModifier);
    public final ConfigInt blastFurnaceFuelConsumption = i(600, 1, "blastFurnaceFuelConsumption", Comments.blastFurnaceFuelConsumption);

    @Override
    public String getName() {
        return "machines";
    }


    private static class Comments {
        static String fireExtinguisherClearRadius = "Changes the radius fire extinguishers can remove fire in.";
        static String largeGenerator = "Determines how powerful the large generator is.";
        static String generator = "Determines how powerful the generator is.";
        static String largeGeneratorMinSpeed = "Changes the lowest speed the large generator can work on.";
        static String largeGeneratorVoltageMultiplier = "Multiplier applied to the large generator's generation when computing output voltage. Raise it to push higher voltage (and therefore current) into low-resistance loads.";
        static String largeGeneratorPowerMultiplier = "Multiplier applied to the large generator's generation when computing output power. Raise it when the network reports 'Not Enough Power' under heavy loads.";
        static String largeGeneratorMaxVoltage = "Maximum output voltage of the large generator. Increase to extend the usable RPM range.";
        static String generatorMinSpeed = "Changes the lowest speed the generator can work on.";
        static String generatorMaxVoltage = "Maximum output voltage of the regular generator. Increase to extend the usable RPM range.";
        static String blastFurnaceHeight = "Changes the maximum height of the blast furnace.";
        static String blastFurnaceHeightSpeedModifier = "Sets the maximum time that can be saved by increasing blast furnace height.";
        static String blastFurnaceFuelConsumption = "Determines how many ticks does it take to consume one fuel.";
        static String electricMotorInternalResistance = "Sets the internal resistance of the electric motor.";
        static String cokeOvenMaxSize = "Determines the maximum size of coke ovens.";
        static String accumulatorStorage = "Determines the storage space of accumulators.";
        static String accumulatorVoltage = "Determines the voltage accumulators output.";
        static String accumulatorMaxAmpOutput = "Sets the maximum amperage an accumulator can provide.";
        static String accumulatorChargingRate = "Sets the maximum charging rate of accumulators.";
        static String fireboxExhaustRequirement = "If set to true,fireboxes will require exhaust management.";
        static String fireboxFuelConsumption = "The amount of fuel (mB) a firebox must hold to keep burning. A burning firebox uses 50 mB every 3 seconds.";
        static String electrolysisMinimumCurrent = "The minimum electric current that will make electrolyzers operational.";
        static String engineMaxLength = "The maximum length of engines.";
        static String surfaceScannerScanDepth = "Y level surface scanner scan at.";
        static String FEtoWattTickConversionRate = "How much Forge Energy is in one watt-tick.";
        static String polarizerItemChargingRate = "How much FE can polarizer charge per tick.";
        static String engineLoudness = "Changes the volume of engines.";
    }
}
