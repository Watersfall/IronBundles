package space.bbkr.ironbundles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.util.NbtType;

import net.minecraft.client.item.BundleTooltipData;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.item.TooltipData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BundleItem;
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
				addToBundle(stack, slot.method_32753(slotStack.getCount(), i, playerInventory.player));
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

			ListTag items = tag.getList("Items", 10);
			BundleInventory inv = new BundleInventory(bundle, items);
			int remainder = stack.getCount() - inv.addStack(stack).getCount();
			tag.put("Items", inv.getTags());
			return remainder;
		} else {
			return 0;
		}
	}

	private int getItemOccupancy(ItemStack stack) {
		if (stack.getItem() instanceof BundleItem) {
			return 4 + getBundledStacks(stack).mapToInt((itemStack) -> getItemOccupancy(itemStack) * itemStack.getCount()).sum();
		} else if (stack.getItem() instanceof CustomBundleItem) {
			return 4 + getBundleOccupancy(stack);
		}
		return 64 / stack.getMaxCount();
	}

	private int getBundleOccupancy(ItemStack stack) {
		return new BundleInventory(stack).count;
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

	private class BundleInventory implements Inventory {
		private final ItemStack bundle;
		private final List<ItemStack> stacks = DefaultedList.of();

		public int count;

		private BundleInventory(ItemStack bundle) {
			this.bundle = bundle;
			if (bundle.hasTag() && bundle.getTag().contains("Items")) {
				this.readTags(bundle.getTag().getList("Items", NbtType.COMPOUND));
			}
		}

		private BundleInventory(ItemStack bundle, ListTag tag) {
			this.bundle = bundle;
			this.readTags(tag);
		}

		public ItemStack addStack(ItemStack stack) {
			ItemStack insertStack = stack.copy();
			int itemOccupancy = getItemOccupancy(stack);
			int insertCount = Math.min(stack.getCount(), (maxCapacity - count) / itemOccupancy);
			if (insertCount == 0) return insertStack;
			int remainder = insertStack.getCount() - insertCount;
			insertStack.setCount(insertCount);
			this.addToExistingSlot(insertStack);
			if (insertStack.isEmpty()) {
				ItemStack ret = stack.copy();
				ret.setCount(remainder);
				return ret;
			} else {
				this.addToNewSlot(insertStack);
				if (insertStack.isEmpty()) {
					ItemStack ret = stack.copy();
					ret.setCount(remainder);
					return ret;
				} else {
					insertStack.increment(remainder);
					return insertStack;
				}
			}
		}

		private void addToExistingSlot(ItemStack stack) {
			for(int i = 0; i < this.size(); ++i) {
				ItemStack itemStack = this.getStack(i);
				if (ItemStack.method_31577(itemStack, stack)) {
					this.transfer(stack, itemStack);
					if (stack.isEmpty()) {
						return;
					}
				}
			}

		}

		public void readTags(ListTag tags) {
			for(int i = tags.size(); i >= 0; --i) {
				ItemStack itemStack = ItemStack.fromTag(tags.getCompound(i));
				if (!itemStack.isEmpty()) {
					this.addStack(itemStack);
				}
			}
			this.markDirty();
		}

		public ListTag getTags() {
			ListTag listTag = new ListTag();

			for(int i = 0; i < this.size(); ++i) {
				ItemStack itemStack = this.getStack(i);
				if (!itemStack.isEmpty()) {
					listTag.add(itemStack.toTag(new CompoundTag()));
				}
			}

			return listTag;
		}

		private void transfer(ItemStack source, ItemStack target) {
			int i = Math.min(this.getMaxCountPerStack(), target.getMaxCount());
			int j = Math.min(source.getCount(), i - target.getCount());
			if (j > 0) {
				target.increment(j);
				source.decrement(j);
				this.markDirty();
			}

		}

		private void addToNewSlot(ItemStack stack) {
			this.stacks.add(0, stack.copy());
			stack.setCount(0);
		}

		@Override
		public int size() {
			return stacks.size();
		}

		@Override
		public boolean isEmpty() {
			for (ItemStack stack : stacks) {
				if (!stack.isEmpty()) return false;
			}
			return true;
		}

		@Override
		public ItemStack getStack(int slot) {
			return slot >= 0 && slot < this.stacks.size() ? this.stacks.get(slot) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeStack(int slot, int amount) {
			ItemStack itemStack = Inventories.splitStack(this.stacks, slot, amount);
			if (!itemStack.isEmpty()) {
				this.markDirty();
			}

			return itemStack;
		}

		@Override
		public ItemStack removeStack(int slot) {
			ItemStack itemStack = this.stacks.get(slot);
			if (itemStack.isEmpty()) {
				return ItemStack.EMPTY;
			} else {
				this.stacks.set(slot, ItemStack.EMPTY);
				return itemStack;
			}
		}

		@Override
		public void setStack(int slot, ItemStack stack) {
			if (slot < stacks.size()) {
				this.stacks.set(slot, stack);
			} else {
				this.stacks.add(stack);
			}
			if (!stack.isEmpty() && stack.getCount() > this.getMaxCountPerStack()) {
				stack.setCount(this.getMaxCountPerStack());
			}
		}

		@Override
		public void markDirty() {
			updateCount();
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return true;
		}

		@Override
		public void clear() {
			this.stacks.clear();
			this.markDirty();
		}

		private void updateCount() {
			count = 0;
			for (ItemStack stack : stacks) {
				count += getItemOccupancy(stack) * stack.getCount();
			}
		}
	}
}
