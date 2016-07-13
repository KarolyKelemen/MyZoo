package com.github.mtakelemen.hellodocker.build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;
import org.gradle.api.Project;

public class CommandExecutor {
    private final Project project;
    private final boolean displayOutputStream;
    private final boolean displayErrorStream;

    public CommandExecutor(Project project) {
        this(project, true, true);
    }

    public CommandExecutor(Project project, boolean displayOutputStream, boolean displayErrorStream) {
        this.project = project;
        this.displayOutputStream = displayOutputStream;
        this.displayErrorStream = displayErrorStream;
    }

    public String executeCommand(String... command) throws IOException {
        return executeCommand(null, command);
    }

    public String executeCommand(Map<String, String> env, String... command) throws IOException {
        return executeCommand(env, project.getProjectDir(), command);
    }

    private static void doOnNewThread(Runnable task) {
        new Thread(task).start();
    }

    private static void forwardOutputStreamOnNewThread(InputStream input, OutputStream output) throws IOException {
        doOnNewThread(() -> {
            try {
                forwardOutputStream(input, output);
            } catch (IOException ex) {
                ex.printStackTrace(System.err);
            }
        });
    }

    private static void forwardOutputStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        while (true) {
            int readCount = input.read(buffer);
            if (readCount <= 0) {
                break;
            }

            output.write(buffer, 0, readCount);
            output.flush();
        }
    }

    public String executeCommand(Map<String, String> env, File workingDir, String... command) throws IOException {
        Map<String, String> appliedEnv = env != null ? env : System.getenv();

        ProcessBuilder procBuilder = new ProcessBuilder(command);
        procBuilder.environment().putAll(appliedEnv);
        procBuilder.directory(workingDir);

        procBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);

        Process process = procBuilder.start();
        try {
            ByteArrayOutputStream collectedOutput = new ByteArrayOutputStream(4 * 1024);

            forwardOutputStreamOnNewThread(process.getInputStream(), displayOutputStream
                    ? new MultiOutputStreamWrapper(collectedOutput, System.out)
                    : collectedOutput);
            forwardOutputStreamOnNewThread(process.getErrorStream(), displayErrorStream
                    ? System.err
                    : new MultiOutputStreamWrapper());

            int exitValue = process.waitFor();
            if (exitValue != 0) {
                throw new IllegalStateException("Failed to execute " + Arrays.asList(command) + ". Exit code: " + exitValue);
            }

            return new String(collectedOutput.toByteArray(), Charset.defaultCharset());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }

    private static final class MultiOutputStreamWrapper extends OutputStream {
        private final OutputStream[] wrapped;

        public MultiOutputStreamWrapper(OutputStream... wrapped) {
            this.wrapped = wrapped.clone();
        }

        @Override
        public void write(int b) throws IOException {
            for (OutputStream output: wrapped) {
                output.write(b);
            }
        }

        @Override
        public void close() throws IOException {
            for (OutputStream output: wrapped) {
                output.close();
            }
        }

        @Override
        public void flush() throws IOException {
            for (OutputStream output: wrapped) {
                output.flush();
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            for (OutputStream output: wrapped) {
                output.write(bytes, offset, length);
            }
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            for (OutputStream output: wrapped) {
                output.write(bytes);
            }
        }
    }
}
