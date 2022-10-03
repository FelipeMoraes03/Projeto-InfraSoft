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
    private int lastFrame;

    //Lista das músicas (array dinâmico)
    private String[][] songTableArray = null;
    private ArrayList<String[]> songTableList = new ArrayList<String[]>();
    private ArrayList<Song> songList = new ArrayList<Song>();

    private int currentTime;
    private int totalTime;
    private int currentIndex;
    private Song currentSong;
    private boolean playing = false;
    private boolean paused = true;
    private boolean play = false;
    private boolean musicStopped = true;
    private boolean newMusicPlay = false;

    private final Lock lock = new ReentrantLock();

    //Botão PlayNow
    private final ActionListener buttonListenerPlayNow = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            //Pega o index da música selecionada na janela
            currentIndex = window.getIndexSelectedSong();
            //Reprodução da música
            playNow(currentIndex);
        }
    };

    //Botão Remove
    private final ActionListener buttonListenerRemove = e -> removeSong();

    //Botão AddSong
    private final ActionListener buttonListenerAddSong = e -> addSong();

    //Botão PlayPouse
    private final ActionListener buttonListenerPlayPause = e -> playPause();

    //Botão Stop
    private final ActionListener buttonListenerStop = e -> stop();

    //Botão Next
    private final ActionListener buttonListenerNext = e -> next();

    //Botão Previous
    private final ActionListener buttonListenerPrevious = e -> previous();

    //Botão Shuffle
    private final ActionListener buttonListenerShuffle = e -> {}; //ENTREGA 3

    //Botão Loop
    private final ActionListener buttonListenerLoop = e -> {}; //ENTREGA 3
    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
            released();
        }

        @Override
        public void mousePressed(MouseEvent e) {
            pressed();
        }

        @Override
        public void mouseDragged(MouseEvent e) { ; }
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

    //MÉTODOS AUXILIARES:
    private void removeSong() {
        new Thread( () -> {
            int indexRemoved;

            try {
                lock.lock();
                //Pega o index da música a ser removida na janela
                indexRemoved = window.getIndexSelectedSong();

                //Se a música removida estiver em execução para a execução
                if (indexRemoved == currentIndex) stop();
                else {
                    //Desabilita botão Previous se após remoção não houver música antes da atual
                    if (indexRemoved == 0 && currentIndex == 1) {
                        window.setEnabledPreviousButton(false);
                    }
                    //Desabilita botão Next se após remoção não houver música depois da atual
                    else if (indexRemoved == songList.size()-1 && currentIndex == songList.size()-2) {
                        window.setEnabledNextButton(false);
                    }
                    //Atualiza o index da música em execução caso seja necessário
                    if (indexRemoved < currentIndex) {
                        currentIndex--;
                    }
                }

                //Remove a música da lista de "Song" e da lista de "String[]"
                songList.remove(indexRemoved);
                songTableList.remove(indexRemoved);

                //Atualiza SongTableArray para ser um array bidimensional dinâmico
                songTableArray = new String[songList.size()][6];
                for (int idx = 0; idx < songList.size(); idx++) {
                    songTableArray[idx] = songTableList.get(idx);
                }

                //Atualiza a tabela de músicas na janela
                window.setQueueList(songTableArray);

            } finally {
                lock.unlock();
            }
        }).start();
    }

    private void addSong() {
        new Thread( () -> {
            Song music;
            String[] musicString;
            try {
                lock.lock();
                music = window.openFileChooser();
                if (music != null) {

                    //Adiciona a música na lista de "Song" e na lista de "String[]"
                    songList.add(music);
                    musicString = window.transformSongToString(music);
                    songTableList.add(songTableList.size(), musicString);

                    //Atualiza SongTableArray para ser um array bidimensional dinâmico
                    songTableArray = new String[songList.size()][6];
                    for (int idx = 0; idx < songList.size(); idx++) {
                        songTableArray[idx] = songTableList.get(idx);
                    }

                    //Habilita botão Next se a música atual era a última música
                    if (currentIndex == songList.size()-2 && !musicStopped) {
                        window.setEnabledNextButton(true);
                    }

                    //Atualiza a tabela de músicas na janela
                    window.setQueueList(songTableArray);
                }

            } catch (IOException | BitstreamException | UnsupportedTagException | InvalidDataException ex) {
                throw new RuntimeException(ex);
            } finally {
                lock.unlock();
            }
        }).start();
    }

    private void playPause() {
        //Altera o estado de pausado -> executando
        paused = !paused;
        playing = !playing;
        //Atualiza o botão PlayPause
        window.setPlayPauseButtonIcon(paused ? 0 : 1);
        //Retorna a execução caso o botão clicado seja play
        if (!paused) play();
    }

    private void playNow(int idx) {

        //Verifica se alguma música está tocando quando playNow pressionado
        if (playing) {
            newMusicPlay = true;
        }

        new Thread(() -> {
            try {
                //Inicializa o frame atual como 0
                currentFrame = 0;
                lock.lock();

                //Declara que a música está em execução
                paused = false;
                musicStopped = false;

                //Libera o clique dos botões PlayPause e Stop
                window.setEnabledPlayPauseButton(true);
                window.setEnabledStopButton(true);
                window.setEnabledScrubber(true);

                //Libera o clique do botão next caso tenha música depois na fila
                if (currentIndex < songList.size()-1) {
                    window.setEnabledNextButton(true);
                }
                else {
                    window.setEnabledNextButton(false);
                }

                //Libera o clique do botão previous caso tenha música antes na fila
                if (currentIndex > 0) {
                    window.setEnabledPreviousButton(true);
                }
                else {
                    window.setEnabledPreviousButton(false);
                }

                //Atualiza a janela com os dados da música a ser tocada
                currentSong = songList.get(idx);
                window.setPlayingSongInfo(currentSong.getTitle(), currentSong.getAlbum(), currentSong.getArtist());
                window.setPlayPauseButtonIcon(1);
                window.setEnabledScrubber(true);

                //Cria Decoder e Bitstream e inicia a música
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
            //Indica que a música está tocando
            play = true;
            playing = true;

            //Toca a música enquanto não estiver pausada e não tiver chegado ao final
            while (play && !paused) {
                try {
                    if (newMusicPlay) {
                        newMusicPlay = false;
                        break;
                    }
                    //Atualiza a janela com o tempo atual e tempo total da música
                    currentTime = (int) (currentFrame*currentSong.getMsPerFrame());
                    totalTime = (int) currentSong.getMsLength();
                    window.setTime(currentTime, totalTime);

                    //Verifica a música foi finalizada
                    if (window.getScrubberValue() < currentSong.getMsLength()) {
                        playNextFrame();
                        currentFrame++;
                    }
                    else {
                        play = false;
                        playing = false;
                    }
                } catch (JavaLayerException ex) {
                    System.out.println(ex);
                }
            }

            //Toca a próxima música da lista, caso exista
            if (!play) {
                if (currentIndex < songList.size()-1 && !musicStopped) {
                    //System.out.println("NEXT");
                    currentIndex++;
                    playNow(currentIndex);
                }
                else {
                    //System.out.println("END");
                    stop();
                    window.setPlayPauseButtonIcon(0);
                    window.setQueueList(songTableArray);
                }
            }
        }).start();
    }
    private void stop() {
        //Para a execução da música
        try {
            lock.lock();

            currentFrame = 0;
            play = false;
            playing = false;
            paused = true;
            musicStopped = true;
            //Atualiza a janela para nenhuma música tocando
            window.resetMiniPlayer();
        }
        finally {
            lock.unlock();
        }
    }

    private void next() {
        //Verifica se há alguma música depois na fila
        try {
            lock.lock();

            if (currentIndex < songList.size() - 1) {
                currentIndex++;
                playNow(currentIndex);
            }
        }
        finally {
            lock.unlock();
        }
    }

    private void previous() {
        //Verifica se há alguma música anterior na fila
        try {
            lock.lock();

            if (currentIndex > 0) {
                currentIndex--;
                playNow(currentIndex);
            }
        }
        finally {
            lock.unlock();
        }
    }

    //Métodos para a entrada do mouse
    private void pressed() {
        paused = true;
    }

    private void released() {
        if (!musicStopped) {

            if (newMusicPlay) {
                newMusicPlay = false;
            }
            else {
                //Cria Decoder e Bitstream (idêntico ao playNow)
                try {
                    currentFrame = 0;
                    device = FactoryRegistry.systemRegistry().createAudioDevice();
                    device.open(decoder = new Decoder());
                    bitstream = new Bitstream(currentSong.getBufferedInputStream());
                } catch (JavaLayerException | FileNotFoundException ex) {
                    System.out.println(ex);
                }

                lastFrame = (int) (window.getScrubberValue() / currentSong.getMsPerFrame());

                try {
                    skipToFrame(lastFrame);
                    currentFrame = lastFrame;
                    window.setTime((int) (currentFrame * currentSong.getMsPerFrame()), totalTime);
                } catch (BitstreamException e) {
                    System.out.println(e);
                }

                if (playing) paused = false;
                play();
            }
        }
    }

    //</editor-fold>
}