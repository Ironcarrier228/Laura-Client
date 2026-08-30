package im.laura.ui.schedules.rw.impl;


import im.laura.ui.schedules.rw.Schedule;
import im.laura.ui.schedules.rw.TimeType;

public class SecretMerchantSchedule
        extends Schedule {
    @Override
    public String getName() {
        return "Тайный торговец";
    }

    @Override
    public TimeType[] getTimes() {
        return new TimeType[]{TimeType.FOUR, TimeType.FIVE, TimeType.EIGHT, TimeType.ELEVEN, TimeType.FOURTEEN, TimeType.SEVENTEEN, TimeType.TWENTY, TimeType.TWENTY_THREE};
    }
}
