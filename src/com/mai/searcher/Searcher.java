package com.mai.searcher;


import com.mai.searcher.exceptions.FileReadException;
import com.mai.searcher.exceptions.FileWriteException;
import com.mai.searcher.exceptions.ThreadException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;


public class Searcher {
    private static final int LINES_PER_THREAD = 100_000;
    private static final int THREAD_COUNT = 4;

    public void start() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {

                String filename = "D:\\_BIG_FILES_\\BigFileForJavaLab\\big_file.txt";
                String searchString = "программа";
                int indent = 2;
                String saveFilename = "D:\\_BIG_FILES_\\BigFileForJavaLab\\result.txt";

//                System.out.print("Enter file name: ");
//                String filename = reader.readLine();
//                System.out.print("Enter search string: ");
//                String searchString = reader.readLine();
//                int indent = fetchIndent(reader);
//                System.out.print("Save to: ");
//                String saveFilename = reader.readLine();


                long startTime = new Date().getTime();

                try {
                    List<Match> results = search(filename, searchString, indent);
                    saveResults(saveFilename, results, searchString, indent);

                    System.out.println("Results saved to \"" + saveFilename + "\". Found " + results.size() + ".");
                    System.out.println("Search ends in " + (new Date().getTime() - startTime) + "ms");
                }
                catch (FileNotFoundException|FileReadException|ThreadException|FileWriteException e) {
                    System.out.println(e.getMessage());
                }

                return;//TODO
            }
        }
        catch (IOException e) {
            System.out.println("Console IO error.");
        }
    }

    private int fetchIndent(BufferedReader reader) throws IOException {
        Integer indent = null;
        while (indent == null) {
            System.out.print("Enter lines indent: ");
            try {
                indent = Integer.parseInt(reader.readLine());
                if (indent < 0)
                    throw new NumberFormatException();
            }
            catch (NumberFormatException e) {
                System.out.println("Wrong indent format.");
            }
        }

        return indent;
    }

    private List<Match> search(String filename, String searchString, int lineIndent)
            throws FileNotFoundException, FileReadException, ThreadException
    {
        long fileSize = getFileSize(filename);
        if (fileSize == -1)
            throw new FileNotFoundException("Cant fetch size of file \"" + filename + "\".");

        List<Match> searchResults = new ArrayList<>();
        int needLines = THREAD_COUNT * LINES_PER_THREAD + lineIndent * 2;
        List<String> lines = new ArrayList<>(needLines);
        long startTime = new Date().getTime();
        long charsRemoved = 0;
        int start = 0, linesOffset = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            while (reader.ready()) {
                readLinesTo(reader, lines, needLines);

                searchResults.addAll(executeThreads(lines, searchString, lineIndent, linesOffset, start));

                start = lineIndent;
                int forDelete = Math.min(lines.size(), LINES_PER_THREAD * THREAD_COUNT - lineIndent + start);
                linesOffset += forDelete;
                charsRemoved += charsCount(lines, 0, forDelete) + forDelete;

                lines = new ArrayList<>(lines.subList(forDelete, lines.size()));

                updateStatus(new Date().getTime() - startTime,
                        (double) charsRemoved / fileSize, searchResults.size());
            }
        }
        catch (IOException e) {
            throw new FileReadException("Error while read search file \"" + filename + "\".");
        }
        catch (InterruptedException e) {
            throw new ThreadException("Error while threading.");
        }

        return searchResults;
    }

    private long getFileSize(String filename) {
        try {
            return Files.size(Paths.get(filename));
        }
        catch (IOException e) {
            return -1;
        }
    }

    private void readLinesTo(BufferedReader reader, List<String> lines, int count) throws IOException {
        while (reader.ready() && lines.size() < count)
            lines.add(reader.readLine());
    }

    private List<Match> executeThreads(List<String> lines, String searchString, int lineIndent,
                                         int lineOffset, int start) throws InterruptedException
    {
        List<Match> searchResults = new ArrayList<>();
        SearchThread[] threads = new SearchThread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; ++i) {
            SearchThread thread = new SearchThread(lines, searchString,
                    start + i * LINES_PER_THREAD, LINES_PER_THREAD, lineIndent, lineOffset);
            thread.start();
            threads[i] = thread;
        }

        for (SearchThread thread : threads)
            thread.join();

        for (SearchThread thread: threads)
            searchResults.addAll(thread.getMatches());

        return searchResults;
    }

    private long charsCount(List<String> lines, int start, int end) {
        long result = 0;
        for (int i = start; i < end; ++i)
            result += lines.get(i).length();
        return result;
    }

    private void updateStatus(long timeRunning, double doneStatus, int countMatches) {
        String doneStr = Double.toString(doneStatus * 100);
        if (doneStr.length() > 5)
            doneStr = doneStr.substring(0, 5);

        System.out.println("Running: " + timeRunning + "ms");
        System.out.println("Done: " + doneStr + "%");
        System.out.println("Count of matches: " + countMatches);
    }

    private void saveResults(String filename, List<Match> matches, String searchString, int indent)
            throws FileWriteException
    {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("Search for: ");
            writer.write(searchString);
            writer.write('\n');
            writer.write("Indent: ");
            writer.write(Integer.toString(indent));
            writer.write("\n\n");

            for (Match result : matches) {
                writer.write("At line: ");
                writer.write(Integer.toString(result.getLineOfResult()));

                for (String line : result.getLinesBefore()) {
                    writer.write('\n');
                    writer.write("    ");
                    writer.write(line);
                }
                writer.write("\n--> ");
                writer.write(result.getResult());
                for (String line : result.getLinesAfter()) {
                    writer.write('\n');
                    writer.write("    ");
                    writer.write(line);
                }
                writer.write("\n\n");
            }
        }
        catch (IOException e) {
            throw new FileWriteException("Error while write to \"" + filename + "\".");
        }
    }



    public static void main(String[] args) {
        Searcher searcher = new Searcher();
        searcher.start();
    }

}
