package mcjty.lib.builder;

import mcjty.lib.base.ModBase;
import mcjty.lib.blocks.BaseBlock;
import mcjty.lib.blocks.GenericItemBlock;
import mcjty.lib.multipart.PartSlot;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.state.IProperty;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Build blocks using this class
 */
public class BaseBlockBuilder<T extends BaseBlockBuilder<T>> {

    private static final Pattern COMPILE = Pattern.compile("@", Pattern.LITERAL);
    public static final IProperty<?>[] EMPTY_PROPERTIES = new IProperty<?>[0];

    protected final ModBase mod;
    protected final String registryName;

    protected ItemGroup creativeTabs;

    protected Material material = Material.IRON;
    protected Function<Block, BlockItem> itemBlockFactory = GenericItemBlock::new;

    protected List<IProperty<?>> extraProperties = new ArrayList<>();

    protected IActivateAction action = (world, pos, player, hand, side, hitX, hitY, hitZ) -> false;
    protected IClickAction clickAction = (world, pos, player) -> {};
    //@todo 1.14 protected IGetBoundingBox boundingBox = (state, source, pos) -> FULL_BLOCK_AABB;
    protected IAddCollisionBoxToList cdBoxToList = null;
    protected IGetAIPathNodeType getAIPathNodeType = (state, world, pos) -> null;
    protected ISlotGetter slotGetter = (world, pos, newState) -> PartSlot.NONE;
    protected IPlacementGetter placementGetter = (world, pos, facing, hitX, hitY, hitZ, meta, placer) -> null;

    protected InformationString informationString;
    protected InformationString informationStringWithShift;

    protected Set<BlockFlags> flags = new HashSet<>();
    protected int lightValue = -1;

    protected BaseBlock.RotationType rotationType = BaseBlock.RotationType.ROTATION;

    public BaseBlockBuilder(ModBase mod, String registryName) {
        this.mod = mod;
        this.registryName = registryName;
    }

    public T creativeTabs(ItemGroup creativeTabs) {
        this.creativeTabs = creativeTabs;
        return (T) this;
    }

    public T material(Material material) {
        this.material = material;
        return (T) this;
    }

    public T itemBlockFactory(Function<Block, BlockItem> itemBlockFactory) {
        this.itemBlockFactory = itemBlockFactory;
        return (T) this;
    }

    public T info(String informationString) {
        this.informationString = new InformationString(informationString);
        return (T) this;
    }

    public T infoParameter(Function<ItemStack, String> parameter) {
        this.informationString.addParameter(parameter);
        return (T) this;
    }

    public T infoExtended(String informationString) {
        this.informationStringWithShift = new InformationString(informationString);
        return (T) this;
    }

    public T infoExtendedParameter(Function<ItemStack, String> parameter) {
        this.informationStringWithShift.addParameter(parameter);
        return (T) this;
    }

    public T property(IProperty<?> property) {
        extraProperties.add(property);
        return (T) this;
    }

    public T flags(BlockFlags... flags) {
        Collections.addAll(this.flags, flags);
        return (T) this;
    }

    public T rotationType(BaseBlock.RotationType rotationType) {
        this.rotationType = rotationType;
        return (T) this;
    }

    public T lightValue(int v) {
        this.lightValue = v;
        return (T) this;
    }

    public T slotGetter(ISlotGetter getter) {
        this.slotGetter = getter;
        return (T) this;
    }

    public T placementGetter(IPlacementGetter getter) {
        this.placementGetter = getter;
        return (T) this;
    }

    public T clickAction(IClickAction action) {
        this.clickAction = action;
        return (T) this;
    }

    public T activateAction(IActivateAction action) {
        this.action = action;
        return (T) this;
    }

    public T getAIPathNodeType(IGetAIPathNodeType getter) {
        this.getAIPathNodeType = getter;
        return (T) this;
    }

    public T addCollisionBoxToList(IAddCollisionBoxToList list) {
        this.cdBoxToList = list;
        return (T) this;
    }

    public T boundingBox(IGetBoundingBox boundingBox) {
        // @todo 1.14
//        this.boundingBox = boundingBox;
        return (T) this;
    }

    public BaseBlock build() {
        IProperty<?>[] properties = calculateProperties();
        ICanRenderInLayer canRenderInLayer = getCanRenderInLayer();
        IGetLightValue getLightValue = getGetLightValue();
        ISideRenderControl renderControl = getSideRenderControl();
        IAddCollisionBoxToList boxToList = getAddCollisionBoxToList();

        BaseBlock block = new BaseBlock(mod, material, registryName, itemBlockFactory) {
            @Override
            protected IProperty<?>[] getProperties() {
                return properties;
            }

            @Override
            public RotationType getRotationType() {
                return rotationType;
            }

            @Override
            public boolean canRenderInLayer(BlockState state, BlockRenderLayer layer) {
                return canRenderInLayer.canRenderInLayer(state, layer);
            }

            @Override
            public int getLightValue(BlockState state, IBlockReader world, BlockPos pos) {
                return getLightValue.getLightValue(state, world, pos);
            }

            @Override
            public boolean doesSideBlockRendering(BlockState state, IBlockReader world, BlockPos pos, Direction face) {
                return renderControl.doesSideBlockRendering(state, world, pos, face);
            }

            @Override
            public void onBlockClicked(World worldIn, BlockPos pos, PlayerEntity playerIn) {
                clickAction.doClick(worldIn, pos, playerIn);
            }

            @Nonnull
            @Override
            public PartSlot getSlotFromState(World world, BlockPos pos, BlockState newState) {
                return slotGetter.getSlotFromState(world, pos, newState);
            }

            @Override
            public BlockState getStateForPlacement(World worldIn, BlockPos pos, Direction facing, float hitX, float hitY, float hitZ, int meta, MobEntity placer) {
                BlockState state = placementGetter.getStateForPlacement(worldIn, pos, facing, hitX, hitY, hitZ, meta, placer);
                if (state != null) {
                    return state;
                }
                return super.getStateForPlacement(worldIn, pos, facing, hitX, hitY, hitZ, meta, placer);
            }

            @Override
            public boolean onBlockActivated(World worldIn, BlockPos pos, BlockState state, PlayerEntity playerIn, Hand hand, Direction facing, float hitX, float hitY, float hitZ) {
                if (!action.doActivate(worldIn, pos, playerIn, hand, facing, hitX, hitY, hitZ)) {
                    return super.onBlockActivated(worldIn, pos, state, playerIn, hand, facing, hitX, hitY, hitZ);
                } else {
                    return true;
                }
            }

            @Override
            public AxisAlignedBB getBoundingBox(BlockState state, IBlockReader source, BlockPos pos) {
                return boundingBox.getBoundingBox(state, source, pos);
            }

            @Override
            public void addCollisionBoxToList(BlockState state, World worldIn, BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes, @Nullable Entity entityIn, boolean isActualState) {
                if (!boxToList.addCollisionBoxToList(state, worldIn, pos, entityBox, collidingBoxes, entityIn, isActualState)) {
                    super.addCollisionBoxToList(state, worldIn, pos, entityBox, collidingBoxes, entityIn, isActualState);
                }
            }

            @Nullable
            @Override
            public PathNodeType getAiPathNodeType(BlockState state, IBlockReader world, BlockPos pos) {
                PathNodeType type = getAIPathNodeType.getAiPathNodeType(state, world, pos);
                if (type == null) {
                    return super.getAiPathNodeType(state, world, pos);
                }
                return type;
            }
        };
        setupBlock(block);
        return block;
    }

    protected IGetLightValue getGetLightValue() {
        if (lightValue == -1) {
            return (state, world, pos) -> state.getLightValue();
        } else {
            return (state, world, pos) -> lightValue;
        }
    }

    protected IAddCollisionBoxToList getAddCollisionBoxToList() {
        if (cdBoxToList != null) {
            return cdBoxToList;
        } else if (flags.contains(BlockFlags.NO_COLLISION)) {
            return (state, worldIn, pos, entityBox, collidingBoxes, entityIn, isActualState) -> true;
        } else {
            return (state, worldIn, pos, entityBox, collidingBoxes, entityIn, isActualState) -> false;
        }
    }

    protected ISideRenderControl getSideRenderControl() {
        if (flags.contains(BlockFlags.RENDER_NOSIDES)) {
            return (state, world, pos, face) -> world.getBlockState(pos.offset(face)).equals(state);
        } else {
            return (state, world, pos, face) -> state.isOpaqueCube();
        }
    }

    protected ICanRenderInLayer getCanRenderInLayer() {
        BlockRenderLayer layer = null;
        if (flags.contains(BlockFlags.RENDER_CUTOUT)) {
            layer = BlockRenderLayer.CUTOUT;
        } else if (flags.contains(BlockFlags.RENDER_TRANSLUCENT)) {
            layer = BlockRenderLayer.TRANSLUCENT;
        }
        ICanRenderInLayer canRenderInLayer;
        if (layer != null) {
            BlockRenderLayer finalLayer = layer;
            if (flags.contains(BlockFlags.RENDER_SOLID)) {
                canRenderInLayer = (state, layer1) -> layer1 == BlockRenderLayer.SOLID || layer1 == finalLayer;
            } else {
                canRenderInLayer = (state, layer1) -> layer1 == finalLayer;
            }
        } else {
            canRenderInLayer = (state, layer1) -> layer1 == BlockRenderLayer.SOLID;
        }
        return canRenderInLayer;
    }

    protected IProperty<?>[] calculateProperties() {
        IProperty<?>[] properties = BaseBlock.getProperties(rotationType);
        IProperty<?>[] additionalProperties = getAdditionalProperties();

        if (!extraProperties.isEmpty() || additionalProperties.length > 0) {
            List<IProperty<?>> newProperties = new ArrayList<>();
            Collections.addAll(newProperties, properties);
            Collections.addAll(newProperties, additionalProperties);
            for (IProperty<?> property : extraProperties) {
                newProperties.add(property);
            }
            properties = newProperties.toArray(new IProperty[newProperties.size()]);
        }
        return properties;
    }

    protected IProperty<?>[] getAdditionalProperties() {
        return EMPTY_PROPERTIES;
    }

    protected void setupBlock(BaseBlock block) {
        block.setCreative(flags.contains(BlockFlags.CREATIVE));
        if (creativeTabs != null) {
            block.setCreativeTab(creativeTabs);
        }
        final boolean opaque = !flags.contains(BlockFlags.NON_OPAQUE);
        block.setOpaqueCube(opaque);

        final boolean full = !flags.contains(BlockFlags.NON_FULLCUBE);
        block.setFullcube(full);

        block.setInformationString(informationString);
        if (informationStringWithShift != null) {
            block.setInformationStringWithShift(informationStringWithShift);
        } else {
            block.setInformationStringWithShift(informationString);
        }
    }
}
