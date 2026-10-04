package com.drmangotea.tfmg.config;



public class TFMGCommonConfig extends net.createmod.catnip.config.ConfigBase {

    public final MachineConfig machines = nested(0, MachineConfig::new, "Config options for TFMG's machinery");
    public final DepositConfig worldgen = nested(1, DepositConfig::new, "Worldgen Settings");
    public final ConfigBool giveHandbookOnFirstJoin = b(true, "giveHandbookOnFirstJoin",
            "Give every player The Factory Handbook, the in-game guide to the mod, the first time they join a world.");

    @Override
    public String getName() {
        return "common";
    }


}
