package io.github.thewebcode.mixin;

import io.github.thewebcode.gui.ServerHoneyClientStartupLoadingScreen;
import io.github.thewebcode.honey.netty.HoneyClient;
import io.github.thewebcode.honey.netty.packet.impl.RequestServerConnectionC2SPacket;
import io.github.thewebcode.honey.netty.packet.impl.RequestServerConnectionS2CPacket;
import io.github.thewebcode.honey.netty.packet.impl.responder.RequestServerConnectionResponderPacket;
import io.github.thewebcode.networking.HoneyClientManagingService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.TimerTask;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin {
	@Shadow
	private ServerInfo selectedEntry;
	private Timer timer;
	private ServerInfo toConnect;

	@Overwrite
	public void connect(ServerInfo entry) {
		this.timer = new Timer();
		this.toConnect = entry;

		try {
			MinecraftClient minecraftClient = MinecraftClient.getInstance();
			minecraftClient.execute(() -> minecraftClient.setScreen(new ServerHoneyClientStartupLoadingScreen("Loading...")));
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					minecraftClient.execute(() -> minecraftClient.setScreen(new ServerHoneyClientStartupLoadingScreen("Starting Local Honey Client...")));
					startHoneyClient(entry);
				}
			}, 1000 * 5);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void startHoneyClient(ServerInfo entry) {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				HoneyClientManagingService honeyClientManagingService = HoneyClientManagingService.get();
				MinecraftClient minecraftClient = MinecraftClient.getInstance();

				ServerAddress serverAddress = ServerAddress.parse(entry.address);
				InetSocketAddress address = new InetSocketAddress(serverAddress.getAddress().equalsIgnoreCase("localhost") ? "127.0.0.1" : serverAddress.getAddress(), 2323);
				honeyClientManagingService.secureStartWithCallback(address, (future) -> {
					finalizeClientStartup();
				});
			}
		}, 5 * 1000);
	}

	private void finalizeClientStartup() {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(new ServerHoneyClientStartupLoadingScreen("Waiting for the server to be ready...")));
				requestServerConnection();
			}
		}, 4 * 1000);
	}

	private void requestServerConnection() {
		HoneyClientManagingService honeyClientManagingService = HoneyClientManagingService.get();
		HoneyClient client = honeyClientManagingService.getHoneyClient();

		RequestServerConnectionC2SPacket packetToSend = new RequestServerConnectionC2SPacket();

		TimerTask timeoutTask = new TimerTask() {
			@Override
			public void run() {
				MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(new ServerHoneyClientStartupLoadingScreen("Response took too long")));
				toConnect = null;
				returnToTitleScreen();
			}
		};

		timer.schedule(timeoutTask, 10 * 1000);
		RequestServerConnectionResponderPacket requestPacket = new RequestServerConnectionResponderPacket(packetToSend, RequestServerConnectionS2CPacket.class, (received -> {
			timeoutTask.cancel();
			boolean b = received.shouldJoin();
			System.out.printf("Received should join Packet with value: %s%n", b);
			if (b) {
				connectToServer();
			}
		}));

		requestPacket.send(client.getConnectedChannel());
	}

	private void connectToServer() {
		MinecraftClient.getInstance().execute(() -> ConnectScreen.connect(new ServerHoneyClientStartupLoadingScreen("Connecting..."), MinecraftClient.getInstance(), ServerAddress.parse(toConnect.address), toConnect, false));
	}

	private void returnToTitleScreen() {
		this.toConnect = null;
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(new MultiplayerScreen(new TitleScreen())));
			}
		}, 3 * 1000);
	}
}