package space.bbkr.ironbundles;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;

public class IronBundlesClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		FabricModelPredicateProviderRegistry.register(IronBundles.IRON_BUNDLE, new Identifier("filled"), (itemStack, clientWorld, livingEntity, i) -> ((CustomBundleItem) IronBundles.IRON_BUNDLE).getAmountFilled(itemStack));
		FabricModelPredicateProviderRegistry.register(IronBundles.GOLD_BUNDLE, new Identifier("filled"), (itemStack, clientWorld, livingEntity, i) -> ((CustomBundleItem) IronBundles.GOLD_BUNDLE).getAmountFilled(itemStack));
		FabricModelPredicateProviderRegistry.register(IronBundles.DIAMOND_BUNDLE, new Identifier("filled"), (itemStack, clientWorld, livingEntity, i) -> ((CustomBundleItem) IronBundles.DIAMOND_BUNDLE).getAmountFilled(itemStack));
		FabricModelPredicateProviderRegistry.register(IronBundles.NETHERITE_BUNDLE, new Identifier("filled"), (itemStack, clientWorld, livingEntity, i) -> ((CustomBundleItem) IronBundles.NETHERITE_BUNDLE).getAmountFilled(itemStack));
	}
}
