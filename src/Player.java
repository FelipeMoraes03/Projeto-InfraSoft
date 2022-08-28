import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;

import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

import java.io.IOException;
import java.util.ArrayList;

public class Player {

    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice where audio samples are written to.
     */
    private AudioDevice device;

    private PlayerWindow window;

    private int currentFrame = 0;

    private String[][] songTableArray = null; //PRIMEIRO IDX PRECISA SER DINÂMICO
    private ArrayList<String[]> songTableList = new ArrayList<String[]>();

    private ArrayList<Song> songList = new ArrayList<Song>();

    private final ActionListener buttonListenerPlayNow = e -> {
        new Thread( () -> {
            System.out.println("PLAY-NOW");
        }).start();
    };
    private final ActionListener buttonListenerRemove = e -> {
        new Thread( () -> {
            //System.out.println("REMOVE");
            int indexRemoved;
            indexRemoved = window.getIndexSelectedSong();

            //REMOVE OS ELEMENTOS LA LISTA DE "Song" E DA LISTA DE "String[]"
            songList.remove(indexRemoved);
            songTableList.remove(indexRemoved);

            //ARRAY COM LINHAS DINÂMICAS
            songTableArray = new String[songList.size()][6];
            for (int idx = 0; idx < songList.size(); idx++) {
                songTableArray[idx] = songTableList.get(idx);
            }

            //ATUALIZAR A TABELA DAS MÚSICAS
            window.setQueueList(songTableArray);

        }).start();
    };
    private final ActionListener buttonListenerAddSong = e -> {
        new Thread( () -> {
            //System.out.println("ADD");
            Song music;
            String[] musicString;
            try {
                music = window.openFileChooser();
                if (music != null) {
                    //ADICIONA OS ELEMENTOS NAS LISTAS DE "Song" E DE "String[]"
                    songList.add(music);
                    musicString = window.transformSongToString(music);
                    songTableList.add(songTableList.size(), musicString);

                    //ARRAY COM LINHAS DINÂMICAS
                    songTableArray = new String[songList.size()][6];
                    for (int idx = 0; idx < songList.size(); idx++) {
                        songTableArray[idx] = songTableList.get(idx);
                    }

                    //ATUALIZAR A TABELA DAS MÚSICAS
                    window.setQueueList(songTableArray);
                }

            } catch (IOException | BitstreamException | UnsupportedTagException | InvalidDataException ex) {
                throw new RuntimeException(ex);
            }
        }).start();
    };
    private final ActionListener buttonListenerPlayPause = e -> {
        new Thread( () -> {
            System.out.println("PLAY/PAUSE");
        }).start();
    };
    private final ActionListener buttonListenerStop = e -> {
        new Thread( () -> {
            System.out.println("STOP");
        }).start();
    };
    private final ActionListener buttonListenerNext = e -> {}; //ENTREGA 2
    private final ActionListener buttonListenerPrevious = e -> {}; //ENTREGA 2
    private final ActionListener buttonListenerShuffle = e -> {}; //ENTREGA 3
    private final ActionListener buttonListenerLoop = e -> {}; //ENTREGA 3
    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
        }

        @Override
        public void mousePressed(MouseEvent e) {
        }

        @Override
        public void mouseDragged(MouseEvent e) {
        }
    };

    public Player() {
        EventQueue.invokeLater(() -> window = new PlayerWindow(
                "Reprodutor Musical",
                songTableArray,
                buttonListenerPlayNow,
                buttonListenerRemove,
                buttonListenerAddSong,
                buttonListenerShuffle,
                buttonListenerPrevious,
                buttonListenerPlayPause,
                buttonListenerStop,
                buttonListenerNext,
                buttonListenerLoop,
                scrubberMouseInputAdapter)
        );
    }

    //<editor-fold desc="Essential">

    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        // TODO Is this thread safe?
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
        }
        return true;
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException Generic Bitstream exception.
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO Is this thread safe?
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
    }
    //</editor-fold>
}
