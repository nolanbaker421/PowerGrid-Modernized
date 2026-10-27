package com.nolanbaker.pgmodernized.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client-only entry points called from common code behind an {@code isClientSide} check. */
public final class ClientHooks {
    private ClientHooks() {}

    /** Draws the coverage of the antenna at {@code pos} for a minute. */
    public static void survey(BlockPos pos) {
        CoverageSurvey.start(pos);
    }

    public static void clearSurvey() {
        CoverageSurvey.clear();
    }

    /** Opens the splice editor for a conduit box or breaker panel. */
    public static void openSplices(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SpliceScreen(pos));
    }

    /** Opens the fire alarm panel's screen: its devices, their zones, and the buttons. */
    public static void openFireAlarm(BlockPos pos) {
        Minecraft.getInstance().setScreen(new FireAlarmScreen(pos));
    }

    /** Opens a controls cabinet: its rail, its door and the wiring between them. */
    public static void openControls(BlockPos pos) {
        Minecraft.getInstance().setScreen(new ControlsCabinetScreen(pos));
    }

    /** Opens an HMI panel large, buttons live. */
    public static void openHmi(BlockPos pos) {
        Minecraft.getInstance().setScreen(new HmiScreen(pos));
    }

    /** Opens a control station's screen: its number and its cells' wiring. */
    public static void openStation(BlockPos pos) {
        Minecraft.getInstance().setScreen(new StationScreen(pos));
    }

    /** Opens a three-phase drive's typed ratings: volts, hertz, ramp. */
    public static void openDriveRatings(BlockPos pos) {
        Minecraft.getInstance().setScreen(new DriveRatingScreen(pos));
    }

    /** Opens an HMI panel's layout editor. */
    public static void openHmiEditor(BlockPos pos) {
        Minecraft.getInstance().setScreen(new HmiEditorScreen(pos));
    }

    /** Opens the typed-setting screen of a load bank. */
    public static void openLoadBank(BlockPos pos) {
        Minecraft.getInstance().setScreen(new LoadBankScreen(pos));
    }

    /** Opens the settings form of a radio, antenna, collector or meter antenna. */
    public static void openSettings(BlockPos pos, String titleKey) {
        Minecraft.getInstance().setScreen(new SettingsScreen(pos, net.minecraft.network.chat.Component.translatable(titleKey)));
    }
}
