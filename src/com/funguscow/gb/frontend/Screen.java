package com.funguscow.gb.frontend;

import com.funguscow.gb.GPU;
import com.funguscow.gb.Keypad;
import com.funguscow.gb.Machine;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.*;

public class Screen extends Canvas implements GPU.GameboyScreen, KeyListener {

    private static Machine machine;

    private final BufferedImage image;
    private BufferStrategy strategy;
    private boolean open = true;
    private JFrame frame;
    public Keypad keypad;

    private long startTime;
    private int numFrames;

    public Screen(){
        startTime = System.currentTimeMillis();
        image = new BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB);
    }

    public boolean isOpen() {
        return open;
    }

    public void makeContainer(){
        frame = new JFrame("Test emulator");
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
        if (++numFrames % 100 == 0) {
            long passed = System.currentTimeMillis() - startTime;
            float fps = (numFrames * 1000f) / passed;
            frame.setTitle(String.format("Fps: %.02f", fps));
        }
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
            case KeyEvent.VK_F1:
                try (OutputStream os = new FileOutputStream("savestate")) {
                    machine.saveState(os);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                break;
            case KeyEvent.VK_F2:
                try (InputStream is = new FileInputStream("savestate")) {
                    machine.loadState(is);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
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

    public static void mainFunc() throws Exception {
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\mario_land.gb";
//        String ROMPath = "D:\\Games\\GBA\\pokemon\\vanilla\\Pokemon red.gb";
//        String ROMPath = "D:\\Games\\GBA\\pokemon\\vanilla\\Pokemon yellow.gbc";
        String ROMPath = "D:\\Games\\GBA\\pokemon\\vanilla\\Pokemon gold.gbc";
//        String ROMPath = "D:\\Games\\GBA\\pokemon\\vanilla\\Pokemon crystal.gbc";
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\tetris.gb";
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\mooneye-test-suite\\build\\emulator-only\\mbc5\\rom_64Mb.gb";
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\dmg_sound\\rom_singles\\01-registers.gb";
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\cpu_instrs\\cpu_instrs.gb";
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\oam_bug\\oam_bug.gb";
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\dmg-acid2.gb";
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\cgb-acid2.gbc";
        machine = new Machine(new File(ROMPath), Machine.MachineMode.GAMEBOY_COLOR);
//        machine.loadRAM(new File("D:\\Games\\GBA\\pokemon\\vanilla\\Pokemon red.ram"));
        Screen screen = new Screen();
        screen.keypad = machine.getKeypad();
        machine.attachScreen(screen);
        screen.makeContainer();
        PcSpeaker speaker = new PcSpeaker();
        machine.attachSpeaker(speaker);
//        try {
//            InputStream source = new FileInputStream("D:\\Games\\GBA\\gbtest\\gameboy-test-data\\cpu_tests\\v1\\01.test");
//            machine.test(source);
//            source.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        while(screen.isOpen()){
            machine.cycle();
        }
        try {
            machine.saveExternal();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Stopped");
    }

    public static void main(String[] args) {
        try {
            mainFunc();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
