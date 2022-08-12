package com.funguscow.gb.frontend;

import com.funguscow.gb.GPU;
import com.funguscow.gb.Keypad;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;

public class Screen extends Canvas implements GPU.GameboyScreen, KeyListener {

    private final BufferedImage image;
    private BufferStrategy strategy;
    private boolean open = true;
    public Keypad keypad;

    public Screen(){
        image = new BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB);
    }

    public boolean isOpen() {
        return open;
    }

    public void makeContainer(){
        JFrame frame = new JFrame("Test emulator");
        frame.addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {

            }

            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("Closing");
                open = false;
            }

            @Override
            public void windowClosed(WindowEvent e) {
            }

            @Override
            public void windowIconified(WindowEvent e) {

            }

            @Override
            public void windowDeiconified(WindowEvent e) {

            }

            @Override
            public void windowActivated(WindowEvent e) {

            }

            @Override
            public void windowDeactivated(WindowEvent e) {

            }
        });
        JPanel panel = (JPanel) frame.getContentPane();
        panel.setPreferredSize(new Dimension(160, 144));
        panel.setLayout(null);
        panel.add(this);
        setBounds(0, 0, 160, 144);
        setIgnoreRepaint(true);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        setFocusable(true);
        requestFocus();
        addKeyListener(this);
        createBufferStrategy(2);
        strategy = getBufferStrategy();
    }

    public Dimension getPreferredSize(){
        return new Dimension(160, 144);
    }

    public void putPixel(int x, int y, int pixel){
        image.setRGB(x, y, pixel);
    }

    public void update(){
        Graphics2D g = (Graphics2D) strategy.getDrawGraphics();
        g.drawImage(image, 0, 0, this);
        g.dispose();
        strategy.show();
    }


    @Override
    public void keyTyped(KeyEvent e) {
        // Does nothing
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if(keypad == null)
            return;
        switch(e.getKeyCode()){
            case KeyEvent.VK_RIGHT:
                keypad.keyDown(Keypad.KEY_RIGHT); break;
            case KeyEvent.VK_LEFT:
                keypad.keyDown(Keypad.KEY_LEFT); break;
            case KeyEvent.VK_UP:
                keypad.keyDown(Keypad.KEY_UP); break;
            case KeyEvent.VK_DOWN:
                keypad.keyDown(Keypad.KEY_DOWN); break;
            case KeyEvent.VK_Z:
                keypad.keyDown(Keypad.KEY_A); break;
            case KeyEvent.VK_X:
                keypad.keyDown(Keypad.KEY_B); break;
            case KeyEvent.VK_C:
                keypad.keyDown(Keypad.KEY_START); break;
            case KeyEvent.VK_D:
                keypad.keyDown(Keypad.KEY_SELECT); break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if(keypad == null)
            return;
        switch(e.getKeyCode()){
            case KeyEvent.VK_RIGHT:
                keypad.keyUp(Keypad.KEY_RIGHT); break;
            case KeyEvent.VK_LEFT:
                keypad.keyUp(Keypad.KEY_LEFT); break;
            case KeyEvent.VK_UP:
                keypad.keyUp(Keypad.KEY_UP); break;
            case KeyEvent.VK_DOWN:
                keypad.keyUp(Keypad.KEY_DOWN); break;
            case KeyEvent.VK_Z:
                keypad.keyUp(Keypad.KEY_A); break;
            case KeyEvent.VK_X:
                keypad.keyUp(Keypad.KEY_B); break;
            case KeyEvent.VK_C:
                keypad.keyUp(Keypad.KEY_START); break;
            case KeyEvent.VK_D:
                keypad.keyUp(Keypad.KEY_SELECT); break;
        }
    }
}
