package net.dravigen.master_of_time;

import api.AddonHandler;
import api.BTWAddon;
import api.world.data.DataEntry;
import api.world.data.DataProvider;
import net.dravigen.master_of_time.commands.CommandTick;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.*;
import org.lwjgl.input.Keyboard;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public class MasterOfTimeAddon extends BTWAddon {
	
	public static final String MoTChannel = "MoT|C2S";
	private static final String PLAYER_OP_NAME = "PlayerOP";
	public static final DataEntry.PlayerDataEntry<Boolean> PLAYER_OP = DataProvider.getBuilder(Boolean.class)
			.name(PLAYER_OP_NAME)
			.defaultSupplier(() -> true)
			.readNBT(NBTTagCompound::getBoolean)
			.writeNBT(NBTTagCompound::setBoolean)
			.player()
			.syncPlayer()
			.buildPlayer();
	private static final String TIME_AFFECTED_NAME = "TickRateAffected";
	public static final DataEntry.PlayerDataEntry<Boolean> TIME_AFFECTED = DataProvider.getBuilder(Boolean.class)
			.name(TIME_AFFECTED_NAME)
			.defaultSupplier(() -> true)
			.readNBT(NBTTagCompound::getBoolean)
			.writeNBT(NBTTagCompound::setBoolean)
			.player()
			.syncPlayer()
			.buildPlayer();
	private static final String MASTER_OF_TIME_DATA_NAME = "MasterOfTimeData";
	public static final DataEntry.WorldDataEntry<float[]> MASTER_OF_TIME_DATA = DataProvider.getBuilder(float[].class)
			.name(MASTER_OF_TIME_DATA_NAME)
			.defaultSupplier(() -> new float[]{10f, 0.25f})
			.readNBT(tag -> {
				if (!tag.hasKey(MASTER_OF_TIME_DATA_NAME)) {
					NBTTagCompound defaultValue = new NBTTagCompound();
					defaultValue.setFloat("increaseValue", 10f);
					defaultValue.setFloat("decreaseValue", 0.25f);
					tag.setCompoundTag(MASTER_OF_TIME_DATA_NAME, defaultValue);
				}
				NBTTagCompound value = tag.getCompoundTag(MASTER_OF_TIME_DATA_NAME);
				return new float[]{value.getFloat("increaseValue"), value.getFloat("decreaseValue")};
			})
			.writeNBT((tag, value) -> {
				NBTTagCompound newValue = new NBTTagCompound();
				newValue.setFloat("increaseValue", value[0]);
				newValue.setFloat("decreaseValue", value[1]);
				tag.setCompoundTag(MASTER_OF_TIME_DATA_NAME, newValue);
			})
			.global()
			.build();
	public static KeyBinding reset_time_speed_key;
	public static KeyBinding upSpeedKey;
	public static KeyBinding downSpeedKey;
	public static KeyBinding freeze_time_speed_key;
	public static KeyBinding step_time_key;
	public static volatile float worldSpeedModifier = 1F;
	public static boolean currentSpeedTest = false;
	public static boolean maxSpeedTest = false;
	public static double tps;
	public static boolean step = false;
	
	public MasterOfTimeAddon() {
		super();
	}
	
	public static float getUpSpeed(WorldServer server) {
		return server.getData(MASTER_OF_TIME_DATA)[0];
	}
	
	public static float getDownSpeed(WorldServer server) {
		return server.getData(MASTER_OF_TIME_DATA)[1];
	}
	
	public static void setIncreaseValue(WorldServer server, float value) {
		server.setData(MASTER_OF_TIME_DATA, new float[]{value, server.getData(MASTER_OF_TIME_DATA)[1]});
	}
	
	public static void setDecreaseValue(WorldServer server, float value) {
		server.setData(MASTER_OF_TIME_DATA, new float[]{server.getData(MASTER_OF_TIME_DATA)[0], value});
	}
	
	public void initKeybind() {
		reset_time_speed_key = new KeyBinding(StatCollector.translateToLocal("Reset time speed"), Keyboard.KEY_R);
		upSpeedKey = new KeyBinding(StatCollector.translateToLocal("Speed up key"), Keyboard.KEY_G);
		downSpeedKey = new KeyBinding(StatCollector.translateToLocal("Slow down key"), Keyboard.KEY_V);
		freeze_time_speed_key = new KeyBinding(StatCollector.translateToLocal("Freeze time"), Keyboard.KEY_F);
		step_time_key = new KeyBinding(StatCollector.translateToLocal("Step 1 tick"), Keyboard.KEY_N);
		
	}
	
	public void preInitialize() {
		this.modID = "MoT";
		MASTER_OF_TIME_DATA.register();
		TIME_AFFECTED.register();
		PLAYER_OP.register();
	}
	
	@Override
	public void initialize() {
		AddonHandler.logMessage(this.getName() + " Version " + this.getVersionString() + " Initializing...");
		registerAddonCommand(new CommandTick());
		
		if (!MinecraftServer.getIsServer()) {
			initKeybind();
		}
		
		this.registerPacketHandler(MoTChannel, (packet, player) -> {
			ByteArrayInputStream bis = new ByteArrayInputStream(packet.data);
			DataInputStream dis = new DataInputStream(bis);
			
			String receivedMessage = dis.readUTF();
			
			// --- STUFF ON SERVER HERE ---
			
			MinecraftServer server = MinecraftServer.getServer();
			String[] splitText = receivedMessage.split(":");
			String subChannel = splitText[0];
			if true {
				WorldServer worldServer = server.worldServers[0];
				switch (subChannel) {
					case "reset" -> {
						worldSpeedModifier = 1;
						server.getConfigurationManager()
								.sendChatMsg(ChatMessageComponent.createFromText("The game is running normally"));
					}
					case "increase" -> {
						float value = getUpSpeed(worldServer);
						
						if (value > 1) {
							worldSpeedModifier = value;
							server.getConfigurationManager()
									.sendChatMsg(ChatMessageComponent.createFromText("The tick rate 'goal' got set to " +
																							 String.format("%.2f",
																										   value) +
																							 "x (" +
																							 String.format("%.1f",
																										   value * 20) +
																							 " t/s)"));
						}
						else {
							player.sendChatToPlayer(ChatMessageComponent.createFromText(
											"Up key speed is too low ! It should be at least above 1x (20 t/s) !")
															.setColor(EnumChatFormatting.RED));
						}
					}
					case "decrease" -> {
						float value = getDownSpeed(worldServer);
						if (value < 1) {
							worldSpeedModifier = value;
							server.getConfigurationManager()
									.sendChatMsg(ChatMessageComponent.createFromText("The tick rate 'goal' got set to " +
																							 String.format("%.2f",
																										   value) +
																							 "x (" +
																							 String.format("%.1f",
																										   value * 20) +
																							 " t/s)"));
						}
						else {
							player.sendChatToPlayer(ChatMessageComponent.createFromText(
											"Down key speed is too high ! It should be at least below 1x (20 t/s) !")
															.setColor(EnumChatFormatting.RED));
						}
					}
				}
			}
		});
	}
}


