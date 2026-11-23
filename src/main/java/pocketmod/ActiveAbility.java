package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Способность, которая работает непрерывно и тратит ману: тепловизор, щит, Спектор, полёт и т.д.
 *
 * Общий цикл (ActiveAbilities.tick) каждый тик: если способность активна и платит через реестр, списывает
 * drainPerSec/20 маны; при нехватке маны вызывает onManaExhausted (выключение и сообщение) и пропускает tick;
 * иначе вызывает tick. Добавить новую способность: реализовать интерфейс и добавить одну строку в ActiveAbilities.
 */
public interface ActiveAbility {

    /** Короткий идентификатор (для логов и отладки). */
    String id();

    /** Активна ли способность прямо сейчас (для платящих через реестр: тратит ли она ману в этот тик). */
    boolean isActive(EntityPlayer p);

    /** Расход маны в секунду (без учёта того, платит ли реестр). Нужен и для отображения суммарного расхода. */
    double drainPerSec(EntityPlayer p);

    /**
     * Вызывается каждый тик (даже когда способность не активна): эффекты, частицы, остывание.
     * Для платящих через реестр вызывается ПОСЛЕ успешной оплаты; ману здесь не списывать.
     */
    void tick(EntityPlayerMP p);

    /** true: ману списывает общий цикл. false: способность платит сама внутри tick (особые случаи). */
    default boolean chargedByRegistry() {
        return true;
    }

    /** Мана кончилась: выключить способность и показать сообщение. */
    default void onManaExhausted(EntityPlayerMP p) {
    }

    /** Ключи булевых флагов в данных сущности, которые нужно синхронизировать с клиентом. */
    default String[] syncedFlags() {
        return new String[0];
    }
}
