package avilaos.vista.gui;

import avilaos.config.ArchivoConfiguracion;
import java.awt.Component;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Selectores de archivo para guardar y cargar la configuración, con sus avisos. */
final class SelectorConfiguracion {

    private static final String NOMBRE_BASE = "configuracion_";

    private SelectorConfiguracion() {
    }

    /** Pide la ruta donde guardar (sugiere un nombre libre y confirma si sobrescribe); null si se cancela. */
    static File elegirParaGuardar(Component padre) {
        File carpeta = carpetaConfig();
        JFileChooser selector = nuevoSelector(carpeta);
        selector.setDialogTitle("Guardar configuración");
        selector.setSelectedFile(new File(carpeta, nombreLibre(carpeta)));
        while (selector.showSaveDialog(padre) == JFileChooser.APPROVE_OPTION) {
            File archivo = selector.getSelectedFile();
            if (!archivo.getName().toLowerCase().endsWith(".json")) {
                archivo = new File(archivo.getParentFile(), archivo.getName() + ".json");
            }
            if (!archivo.exists() || confirmarSobrescribir(padre, archivo)) {
                return archivo;
            }
        }
        return null;
    }

    /** Pide el archivo a cargar; null si se cancela. */
    static File elegirParaCargar(Component padre) {
        JFileChooser selector = nuevoSelector(carpetaConfig());
        selector.setDialogTitle("Cargar configuración");
        if (selector.showOpenDialog(padre) == JFileChooser.APPROVE_OPTION) {
            return selector.getSelectedFile();
        }
        return null;
    }

    /** Muestra un mensaje de error modal. */
    static void error(Component padre, String titulo, String mensaje) {
        JOptionPane.showMessageDialog(padre, mensaje, titulo, JOptionPane.ERROR_MESSAGE);
    }

    private static boolean confirmarSobrescribir(Component padre, File archivo) {
        int respuesta = JOptionPane.showConfirmDialog(padre,
                "Ya existe \"" + archivo.getName() + "\". ¿Quieres sobrescribirlo?",
                "Sobrescribir archivo", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return respuesta == JOptionPane.YES_OPTION;
    }

    private static JFileChooser nuevoSelector(File carpeta) {
        JFileChooser selector = new JFileChooser(carpeta);
        selector.setAcceptAllFileFilterUsed(false);
        selector.setFileFilter(new FileNameExtensionFilter("Configuración JSON (*.json)", "json"));
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
