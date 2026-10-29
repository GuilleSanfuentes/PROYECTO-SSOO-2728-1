package avilaos.vista.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;

/** Botón sin recuadro de foco y con un leve oscurecimiento al pasar el mouse. */
public class BotonSutil extends JButton {

    private static final Color SOMBRA = new Color(0, 0, 0, 22);

    private boolean encima;

    /** Crea el botón con el texto indicado. */
    public BotonSutil(String texto) {
        super(texto);
        setFocusPainted(false);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                encima = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                encima = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (encima && isEnabled()) {
            g.setColor(SOMBRA);
            g.fillRect(2, 2, getWidth() - 4, getHeight() - 4);
        }
    }
}
