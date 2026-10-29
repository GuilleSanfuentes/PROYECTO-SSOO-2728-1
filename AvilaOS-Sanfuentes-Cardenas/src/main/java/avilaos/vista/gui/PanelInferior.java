package avilaos.vista.gui;

import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

/** Zona inferior redimensionable con una pestaña por vista global: buffers, log, consola, métricas y gráfico. */
public class PanelInferior extends JTabbedPane {

    /** Arma las pestañas; cada una se llena en su parte correspondiente. */
    public PanelInferior() {
        agregar("Buffers", "Estado de cada buffer (parte 4)");
        agregar("Log", "Log de eventos (parte 4)");
        agregar("Consola", "Salida del Debug (parte 4)");
        agregar("Métricas", "Métricas por computador y globales (parte 7)");
        agregar("Gráfico", "Utilización de CPU en el tiempo (parte 7)");
    }

    private void agregar(String titulo, String pendiente) {
        JLabel aviso = new JLabel(pendiente, SwingConstants.CENTER);
        aviso.setForeground(new Color(108, 117, 125));
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(aviso, java.awt.BorderLayout.CENTER);
        addTab(titulo, panel);
    }
}
