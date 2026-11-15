package pocketmod;

/** Мост к необязательной интеграции с JourneyMap (подключается, только если JourneyMap API был доступен при сборке). */
public class JmHook {

    public static volatile Runnable refresh;

    public static void fire() {
        Runnable r = refresh;
        if (r != null) {
            try {
                r.run();
            } catch (Throwable ignored) {
            }
        }
    }
}
