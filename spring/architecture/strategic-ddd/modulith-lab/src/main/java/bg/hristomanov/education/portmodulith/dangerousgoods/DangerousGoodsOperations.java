package bg.hristomanov.education.portmodulith.dangerousgoods;

import bg.hristomanov.education.portmodulith.dangerousgoods.events.DangerousGoodsAssessed;

/** Public entry point of the Dangerous Goods module. */
public interface DangerousGoodsOperations {

    DangerousGoodsAssessed assess(String vesselId, boolean documentationComplete);
}
