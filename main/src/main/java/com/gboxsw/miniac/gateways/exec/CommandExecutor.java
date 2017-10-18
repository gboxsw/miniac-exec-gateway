package com.gboxsw.miniac.gateways.exec;

import java.util.concurrent.*;

public interface CommandExecutor {

	public byte[] execute(String command, long timeout, ExecutorService executorService);

}
