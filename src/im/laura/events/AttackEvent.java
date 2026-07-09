package im.laura.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.Entity;

@AllArgsConstructor
@Getter
@Setter
public class AttackEvent {
    public Entity entity;
}
