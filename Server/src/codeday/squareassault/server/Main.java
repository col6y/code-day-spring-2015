package codeday.squareassault.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import codeday.squareassault.protobuf.Messages;
import codeday.squareassault.protobuf.QueueReceiver;
import codeday.squareassault.protobuf.QueueSender;
import codeday.squareassault.protobuf.SharedConfig;

public class Main implements Runnable {

	private static final long TICK_DELAY = 100;
	private final Socket conn;
	private final InputStream input;
	private final OutputStream output;
	private final LinkedBlockingQueue<Messages.ToClient> sendQueue = new LinkedBlockingQueue<>();
	private final int tid;
	private final ServerContext server;

	public Main(ServerContext server, Socket conn, int tid) throws IOException {
		this.server = server;
		this.conn = conn;
		this.tid = tid;
		this.input = conn.getInputStream();
		this.output = conn.getOutputStream();
	}

	public static void main(String[] args) throws IOException {
		ServerContext server = new ServerContext();
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				server.tick();
			}
		}, TICK_DELAY, TICK_DELAY);
		ServerSocket sock = new ServerSocket(SharedConfig.PORT);
		try {
			int n = 0;
			while (true) {
				Socket conn = sock.accept();
				try {
					new Thread(new Main(server, conn, n), "ClientHandler-" + (n)).start();
				} catch (IOException ex) {
					ex.printStackTrace(); // TODO: Better logging system.
				}
				n++;
			}
		} finally {
			sock.close();
		}
	}

	@Override
	public void run() {
		new Thread(new QueueSender<>(sendQueue, output), "Sender-" + tid).start();
		ArrayBlockingQueue<Messages.ToServer> recvQueue = new ArrayBlockingQueue<>(128);
		new Thread(new QueueReceiver<Messages.ToServer>(recvQueue, input, Messages.ToServer.newBuilder()), "Receiver-" + tid).start();
		ClientContext context = server.newClient(this.sendQueue);
		while (true) {
			Messages.ToServer taken;
			try {
				taken = recvQueue.take();
			} catch (InterruptedException e) {
				e.printStackTrace(); // TODO: logging
				continue;
			}
			if (taken == null) {
				break;
			}
			context.receiveMessage(taken);
		}
		server.removeClient(context);
		try {
			input.close();
		} catch (IOException e) {
			e.printStackTrace(); // TODO: logging
		}
		try {
			output.close();
		} catch (IOException e) {
			e.printStackTrace(); // TODO: logging
		}
		try {
			conn.close();
		} catch (IOException e) {
			e.printStackTrace(); // TODO: logging
		}
	}
}