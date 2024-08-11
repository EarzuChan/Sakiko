package teunit.ext;

import io.github.karlatemp.jvmhook.JvmHookFramework;

import java.lang.reflect.Method;

public class Mamba
{
    private static void load() throws NoSuchMethodException
    {
        System.out.println("Mamba loaded");

        System.out.println("Kobe Bryant：" + getKobeCode());

        System.out.println("Mamba out");

        Method method = Mamba.class.getDeclaredMethod("getKobeCode");

        JvmHookFramework.INSTANCE.registerHook(method, call -> {
            System.out.println("Mamba hooked");

            call.earlyReturn()
                .returnInt(8);
        });

        System.out.println("Kobe Bryant：" + getKobeCode());
    }

    public static int getKobeCode()
    {
        return 24;
    }
}
