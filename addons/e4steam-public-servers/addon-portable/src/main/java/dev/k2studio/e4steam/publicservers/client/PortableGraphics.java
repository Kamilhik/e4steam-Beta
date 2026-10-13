package dev.k2studio.e4steam.publicservers.client;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.screens.Screen;

/** Rendering bridge for PoseStack (1.17+) and GuiGraphics (1.19.4+). */
public final class PortableGraphics {
    private static final ConcurrentHashMap<String, Method> METHODS = new ConcurrentHashMap<>();
    private final Object context;
    public PortableGraphics(Object context) { this.context = context; }
    public void fill(int x1, int y1, int x2, int y2, int color) {
        invoke("fill,m_280509_,method_25294,m_93172_", new Object[]{x1,y1,x2,y2,color});
    }
    public void drawString(Object font, Object text, int x, int y, int color, boolean shadow) {
        invoke("drawString,m_280614_,method_51439,m_93243_,method_27535,method_25303", new Object[]{font,text,x,y,color,shadow});
    }
    public void drawCenteredString(Object font, Object text, int x, int y, int color) {
        invoke("drawCenteredString,m_280653_,method_27534,m_93215_", new Object[]{font,text,x,y,color});
    }
    private void invoke(String names, Object[] args) {
        String key = context.getClass().getName() + names;
        Method method = METHODS.get(key);
        if (method == null) {
            method = find(context.getClass(), names, args, false);
            if (method == null) method = find(Screen.class, names, args, true);
            if (method == null) throw new IllegalStateException("Catalog rendering hook missing: " + names);
            method.setAccessible(true);
            METHODS.put(key, method);
        }
        try {
            boolean legacy = Modifier.isStatic(method.getModifiers());
            Object[] actual = arguments(args, legacy, method.getParameterCount());
            method.invoke(legacy ? null : context, actual);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Catalog rendering failed", failure); }
    }
    private Method find(Class<?> start, String names, Object[] args, boolean legacy) {
        for (String name : names.split(",")) {
            for (Class<?> type=start; type!=null; type=type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (!method.getName().equals(name) || Modifier.isStatic(method.getModifiers()) != legacy) continue;
                    Object[] actual=arguments(args, legacy, method.getParameterCount());
                    if (actual.length != method.getParameterCount()) continue;
                    Class<?>[] types=method.getParameterTypes();
                    boolean match=true;
                    for (int i=0;i<types.length;i++) {
                        if (!(types[i].isInstance(actual[i]) || types[i]==int.class && actual[i] instanceof Integer
                                || types[i]==boolean.class && actual[i] instanceof Boolean)) { match=false; break; }
                    }
                    if (match) return method;
                }
            }
        }
        return null;
    }
    private Object[] arguments(Object[] args, boolean legacy, int count) {
        // Older GuiComponent.drawString has no shadow argument.
        int length=args.length;
        if (legacy && length>0 && args[length-1] instanceof Boolean) length--;
        Object[] actual=new Object[length+(legacy?1:0)];
        if (legacy) actual[0]=context;
        System.arraycopy(args,0,actual,legacy?1:0,length);
        return actual;
    }
}
