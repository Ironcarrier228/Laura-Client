package im.laura.command;

public interface CommandProvider {
    Command command(String alias);
}
