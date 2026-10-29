package avilaos.vista.gui;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;

/** Fila de columnas lado a lado; se estira si la ventana es ancha y hace scroll horizontal si no caben. */
public class PanelColumnas extends JPanel implements Scrollable {

    /** Crea la fila con n columnas del mismo ancho. */
    public PanelColumnas(int columnas) {
        super(new GridLayout(1, Math.max(1, columnas), 8, 0));
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientacion, int direccion) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientacion, int direccion) {
        return visible.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport vista && vista.getWidth() >= getPreferredSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return true;
    }
}
