package space.bbkr.ironbundles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.BundleTooltipData;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.item.TooltipData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class CustomBundleItem extends Item {
	private static final int ITEM_BAR_COLOR = MathHelper.packRgb(0.4F, 0.4F, 1.0F);

	private final int maxCapacity;

	public CustomBundleItem(Settings settings, int maxCapacity) {
		super(settings);
		this.maxCapacity = maxCapacity;
	}

	public float getAmountFilled(ItemStack stack) {
		return (float)getBundleOccupancy(stack) / maxCapacity;
	}

	@Override
	public boolean onStackClicked(ItemStack stack, Slot slot, ClickType clickType, PlayerInventory playerInventory) {
		if (clickType != ClickType.RIGHT) {
			return false;
		} else {
			ItemStack slotStack = slot.getStack();
			if (slotStack.isEmpty()) {
				getTopStack(slotStack).ifPresent((itemStack2) -> addToBundle(slotStack, slot.method_32756(itemStack2)));
			} else if (slotStack.getItem().hasStoredInventory()) {
				int i = (maxCapacity - getBundleOccupancy(slotStack)) / getItemOccupancy(slotStack);
				addToBundle(slotStack, slot.method_32753(slotStack.getCount(), i, playerInventory.player));
			}

			return true;
		}
	}

	@Override
	public boolean onClicked(ItemStack stack, ItemStack otherStack, Slot slot, ClickType clickType, PlayerInventory playerInventory) {
		if (clickType == ClickType.RIGHT && slot.method_32754(playerInventory.player)) {
			if (otherStack.isEmpty()) {
				getTopStack(stack).ifPresent(playerInventory::setCursorStack);
			} else {
				otherStack.decrement(addToBundle(stack, otherStack));
			}

			return true;
		} else {
			return false;
		}
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		return dumpBundle(stack, user) ? TypedActionResult.success(stack, world.isClient()) : TypedActionResult.fail(stack);
	}

	@Override
	@Environment(EnvType.CLIENT)
	public boolean isItemBarVisible(ItemStack stack) {
		return getBundleOccupancy(stack) > 0;
	}

	@Override
	@Environment(EnvType.CLIENT)
	public int getItemBarStep(ItemStack stack) {
		return Math.min(13 * getBundleOccupancy(stack) / maxCapacity, 13);
	}

	@Override
	@Environment(EnvType.CLIENT)
	public int getItemBarColor(ItemStack stack) {
		return ITEM_BAR_COLOR;
	}

	private int addToBundle(ItemStack bundle, ItemStack stack) {
		if (!stack.isEmpty() && stack.getItem().hasStoredInventory()) {
			CompoundTag tag = bundle.getOrCreateTag();
			if (!tag.contains("Items")) {
				tag.put("Items", new ListTag());
			}

			int bundleOccupancy = getBundleOccupancy(bundle);
			int itemOccupancy = getItemOccupancy(stack);
			int insertCount = Math.min(stack.getCount(), (maxCapacity - bundleOccupancy) / itemOccupancy);
			if (insertCount == 0) {
				return 0;
			} else {
				ListTag items = tag.getList("Items", 10);
				Optional<CompoundTag> optional = getTopStackOf(stack, items);
				if (optional.isPresent()) {
					CompoundTag item = optional.get();
					ItemStack newStack = ItemStack.fromTag(item);
					newStack.increment(insertCount);
					newStack.toTag(item);
					items.remove(item);
					items.add(0, item);
				} else {
					ItemStack newStack = stack.copy();
					newStack.setCount(insertCount);
					CompoundTag item = new CompoundTag();
					newStack.toTag(item);
					items.add(0, item);
				}

				return insertCount;
			}
		} else {
			return 0;
		}
	}

	private static Optional<CompoundTag> getTopStackOf(ItemStack itemStack, ListTag listTag) {
		if (itemStack.isOf(Items.BUNDLE)) {
			return Optional.empty();
		} else {
			Stream<Tag> stream = listTag.stream();
			stream = stream.filter(CompoundTag.class::isInstance);
			return stream.map(CompoundTag.class::cast).filter((compoundTag) -> ItemStack.method_31577(ItemStack.fromTag(compoundTag), itemStack)).findFirst();
		}
	}

	private int getItemOccupancy(ItemStack stack) {
		return stack.isOf(this) ? 4 + getBundleOccupancy(stack) : 64 / stack.getMaxCount();
	}

	private int getBundleOccupancy(ItemStack stack) {
		return getBundledStacks(stack).mapToInt((itemStack) -> getItemOccupancy(itemStack) * itemStack.getCount()).sum();
	}

	private static Optional<ItemStack> getTopStack(ItemStack itemStack) {
		CompoundTag tag = itemStack.getOrCreateTag();
		if (!tag.contains("Items")) {
			return Optional.empty();
		} else {
			ListTag items = tag.getList("Items", 10);
			if (items.isEmpty()) {
				return Optional.empty();
			} else {
				CompoundTag item = items.getCompound(0);
				ItemStack stack = ItemStack.fromTag(item);
				items.remove(0);
				return Optional.of(stack);
			}
		}
	}

	private static boolean dumpBundle(ItemStack itemStack, PlayerEntity playerEntity) {
		CompoundTag tag = itemStack.getOrCreateTag();
		if (!tag.contains("Items")) {
			return false;
		} else {
			if (playerEntity instanceof ServerPlayerEntity) {
				ListTag items = tag.getList("Items", 10);

				for(int i = 0; i < items.size(); ++i) {
					CompoundTag item = items.getCompound(i);
					ItemStack stack = ItemStack.fromTag(item);
					playerEntity.dropItem(stack, true);
				}
			}

			itemStack.removeSubTag("Items");
			return true;
		}
	}

	private static Stream<ItemStack> getBundledStacks(ItemStack stack) {
		CompoundTag compoundTag = stack.getTag();
		if (compoundTag == null) {
			return Stream.empty();
		} else {
			ListTag listTag = compoundTag.getList("Items", 10);
			Stream<Tag> stream = listTag.stream();
			return stream.map(CompoundTag.class::cast).map(ItemStack::fromTag);
		}
	}

	@Override
	@Environment(EnvType.CLIENT)
	public Optional<TooltipData> getTooltipData(ItemStack stack) {
		DefaultedList<ItemStack> defaultedList = DefaultedList.of();
		getBundledStacks(stack).forEach(defaultedList::add);
		return Optional.of(new BundleTooltipData(defaultedList, getBundleOccupancy(stack) < maxCapacity));
	}

	@Override
	@Environment(EnvType.CLIENT)
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add((new TranslatableText("item.minecraft.bundle.fullness", getBundleOccupancy(stack), maxCapacity)).formatted(Formatting.GRAY));
	}
}
