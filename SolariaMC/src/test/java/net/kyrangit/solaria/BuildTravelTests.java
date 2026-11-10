package net.kyrangit.solaria;

import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.dimension.SolariaDimensions;
import net.kyrangit.solaria.scan.ScanPackets;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.util.HashSet;

/** Synthetic routing/packet contract checks; no worlds or chunks are loaded. */
public final class BuildTravelTests {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var keys = new HashSet<>();
        for (var body : AstronomyClass.values()) {
            var key = SolariaDimensions.keyFor(body);
            check(keys.add(key), "Each body has a distinct dimension");
            check(key.identifier().toString().equals("solariamod:" + body.id()), "Namespaced body key");
            check(SolariaDimensions.classFromDimensionId(key.identifier().toString()) == body, "Dimension lookup round trip");
        }
        check(keys.size() == 10, "Exactly ten body dimensions");
        for (String invalid : new String[]{null, "earth", "minecraft:earth", "solariamod:academy", "solariamod:unknown"})
            check(SolariaDimensions.classFromDimensionId(invalid) == null, "Non-body dimension rejected");
        var fields = ScanPackets.Build.class.getRecordComponents();
        check(fields.length == 2 && fields[0].getName().equals("id") && fields[1].getName().equals("revision"),
            "Build payload contains no client-selected label or coordinates");
        var packet = new ScanPackets.Build("0123456789abcdef0123456789abcdef", 4);
        check(packet.revision() == 4 && packet.id().length() == 32, "Build identity/revision constructor");
        System.out.println("BuildTravelTests: 38 assertions passed (synthetic; no LAN runtime).");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
