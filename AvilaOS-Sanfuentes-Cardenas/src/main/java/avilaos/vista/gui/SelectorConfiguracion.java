package avilaos.vista.gui;

import avilaos.config.ArchivoConfiguracion;
import avilaos.vista.GraficoUtilizacion;
import java.awt.Component;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Selectores de archivo para guardar y cargar la configuración y para exportar el gráfico, con sus avisos. */
final class SelectorConfiguracion {

    private static final String NOMBRE_BASE = "configuracion_";

    private SelectorConfiguracion() {
    }

    /** Pide la ruta donde guardar (sugiere un nombre libre y confirma si sobrescribe); null si se cancela. */
    static File elegirParaGuardar(Component padre) {
        File carpeta = carpetaConfig();
        return elegirRutaNueva(padre, "Guardar configuración", carpeta, nombreLibre(carpeta),
                "Configuración JSON (*.json)", "json");
    }

    /** Pide la ruta del PNG del gráfico (sugiere salida/utilizacion_cpu.png y confirma si sobrescribe); null si se cancela. */
    static File elegirParaExportarPNG(Component padre) {
        File sugerido = new File(GraficoUtilizacion.RUTA_POR_DEFECTO).getAbsoluteFile();
        return elegirRutaNueva(padre, "Exportar gráfico", sugerido.getParentFile(), sugerido.getName(),
                "Imagen PNG (*.png)", "png");
    }

    /** Pide el archivo a cargar; null si se cancela. */
    static File elegirParaCargar(Component padre) {
        JFileChooser selector = nuevoSelector(carpetaConfig(), "Configuración JSON (*.json)", "json");
        selector.setDialogTitle("Cargar configuración");
        if (selector.showOpenDialog(padre) == JFileChooser.APPROVE_OPTION) {
            return selector.getSelectedFile();
        }
        return null;
    }

    /** Muestra un mensaje de error modal. */
    static void error(Component padre, String titulo, String mensaje) {
        Avisos.error(padre, titulo, mensaje);
    }

    private static File elegirRutaNueva(Component padre, String titulo, File carpeta, String nombre,
            String descripcion, String extension) {
        JFileChooser selector = nuevoSelector(carpeta, descripcion, extension);
        selector.setDialogTitle(titulo);
        selector.setSelectedFile(new File(carpeta, nombre));
        while (selector.showSaveDialog(padre) == JFileChooser.APPROVE_OPTION) {
            File archivo = selector.getSelectedFile();
            if (!archivo.getName().toLowerCase().endsWith("." + extension)) {
                archivo = new File(archivo.getParentFile(), archivo.getName() + "." + extension);
            }
            if (!archivo.exists() || Avisos.confirmar(padre, "Sobrescribir archivo",
                    "Ya existe \"" + archivo.getName() + "\". ¿Quieres sobrescribirlo?")) {
                return archivo;
            }
        }
        return null;
    }

    private static JFileChooser nuevoSelector(File carpeta, String descripcion, String extension) {
        JFileChooser selector = new JFileChooser(carpeta);
        selector.setAcceptAllFileFilterUsed(false);
        selector.setFileFilter(new FileNameExtensionFilter(descripcion, extension));
        return selector;
    }

    private static File carpetaConfig() {
        File carpeta = new File(ArchivoConfiguracion.RUTA_POR_DEFECTO).getAbsoluteFile().getParentFile();
        return carpeta != null && carpeta.isDirectory() ? carpeta : new File(".").getAbsoluteFile();
    }

    private static String nombreLibre(File carpeta) {
        int n = 1;
        while (new File(carpeta, NOMBRE_BASE + n + ".json").exists()) {
            n++;
        }
        return NOMBRE_BASE + n + ".json";
    }
}
