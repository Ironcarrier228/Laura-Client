package im.laura.command;

import im.laura.command.impl.DispatchResult;

public interface CommandDispatcher {
    DispatchResult dispatch(String command);
}
