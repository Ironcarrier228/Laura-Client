package im.laura.events;

public class SprintEvent extends CancelEvent {

    private boolean sprintState;

    public void setSprintState(boolean state) {
        this.sprintState = state;
    }

    public boolean getSprintState() {
        return this.sprintState;
    }
}
