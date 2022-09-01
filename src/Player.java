import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;

import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

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

    private int currentTime;
    private int totalTime;
    private int currentIndex;
    private Song currentSong;
    private boolean playing = false;
    private boolean paused = false;
    private boolean play = false;

    private final Lock lock = new ReentrantLock();

    private final ActionListener buttonListenerPlayNow = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            currentIndex = window.getIndexSelectedSong();
            playNow(currentIndex);
        }
    };
    private final ActionListener buttonListenerRemove = e -> {
        new Thread( () -> {
            int indexRemoved;

            try {
                lock.lock();
                indexRemoved = window.getIndexSelectedSong();

                //REMOVE OS ELEMENTOS DA LISTA DE "Song" E DA LISTA DE "String[]"
                songList.remove(indexRemoved);
                songTableList.remove(indexRemoved);

                if (indexRemoved == currentIndex) stop();
                else if (indexRemoved < currentIndex) currentIndex--;

                //ARRAY COM LINHAS DINÂMICAS
                songTableArray = new String[songList.size()][6];
                for (int idx = 0; idx < songList.size(); idx++) {
                    songTableArray[idx] = songTableList.get(idx);
                }

                //ATUALIZAR A TABELA DAS MÚSICAS
                window.setQueueList(songTableArray);

            } finally {
                lock.unlock();
            }
        }).start();
    };
    private final ActionListener buttonListenerAddSong = e -> {
        new Thread( () -> {
            Song music;
            String[] musicString;
            try {
                lock.lock();
                music = window.openFileChooser();
                if (music != null) {
                    //HABILITA OS BOTÕES DA INTERFACE PELA PRIMEIRA VEZ
                    if (songList.size() == 0) {
                        window.setEnabledPlayPauseButton(true);
                        window.setEnabledStopButton(true);
                    }

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
            } finally {
                lock.unlock();
            }
        }).start();
    };
    private final ActionListener buttonListenerPlayPause = e -> {
        paused = !paused;
        playing = !playing;
        window.setPlayPauseButtonIcon(paused ? 1 : 0);
        if (!paused) play();
    };
    private final ActionListener buttonListenerStop = e -> stop();
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

    //MÉTODOS AUXILIARES
    private void playNow(int idx) {
        new Thread( () -> {
            try {
                currentFrame = 0;
                lock.lock();

                playing = true;
                paused = false;

                window.setEnabledPlayPauseButton(true);
                window.setEnabledStopButton(true);

                currentSong = songList.get(idx);
                window.setPlayingSongInfo(currentSong.getTitle(), currentSong.getAlbum(), currentSong.getArtist());
                window.setPlayPauseButtonIcon(0);
                window.setEnabledScrubber(true);

                try {
                    device = FactoryRegistry.systemRegistry().createAudioDevice();
                    device.open(decoder = new Decoder());
                    bitstream = new Bitstream(currentSong.getBufferedInputStream());
                    play();
                } catch (JavaLayerException | FileNotFoundException ex) {
                    System.out.println(ex);
                }
            } finally {
                lock.unlock();
            }
        }).start();
    }
    private void play() {
        new Thread( () -> {
            play = true;
            playing = true;

            while (play && !paused) {
                try {
                    currentTime = (int) (currentFrame*currentSong.getMsPerFrame());
                    totalTime = (int) currentSong.getMsLength();
                    window.setTime(currentTime, totalTime);

                    play = (window.getScrubberValue() < currentSong.getMsLength()) ? playNextFrame() : false;
                    currentFrame++;
                } catch (JavaLayerException ex) {
                    System.out.println(ex);
                }
            }

            if (!play) {
                if (currentIndex < songList.size()-1) {
                    currentIndex++;
                    playNow(currentIndex);
                } else {
                    stop();
                }
            }
        }).start();
    }
    private void stop() {
        currentFrame = 0;
        play = false;
        playing = false;
        paused = true;
        window.resetMiniPlayer();
    }
    //</editor-fold>
}