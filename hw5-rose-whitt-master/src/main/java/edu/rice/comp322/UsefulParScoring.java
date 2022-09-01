package edu.rice.comp322;

import edu.rice.hj.api.*;

import static edu.rice.hj.Module0.*;
import static edu.rice.hj.Module1.*;

/**
 * A scorer that works in parallel.
 */
public class UsefulParScoring extends AbstractDnaScoring {
    /* Fields */
    /**
     * The length of the first sequence.
     */
    private final int xLength;
    /**
     * The length of the second sequence.
     */
    private final int yLength;
    /**
     * The Smith-Waterman matrix.
     */
    private final int[][] s;


    /**
     * <p>main.</p> Takes the names of two files, and in parallel calculates the sequence alignment scores of the two DNA
     * strands that they represent.
     *
     * @param args The names of two files.
     */
    public static void main(final String[] args) throws Exception {
        final ScoringRunner scoringRunner = new ScoringRunner(UsefulParScoring::new);
        scoringRunner.start("UsefulParScoring", args);
    }

    /**
     * Creates a new UsefulParScoring.
     *
     * @param xLength length of the first sequence
     * @param yLength length of the second sequence
     */
    public UsefulParScoring(final int xLength, final int yLength) {
        if (xLength <= 0 || yLength <= 0) {
            throw new IllegalArgumentException("Lengths (" + xLength + ", " + yLength + ") must be positive!");
        }

        /* Initalize global length variables */
        this.xLength = xLength;
        this.yLength = yLength;

        /* Pre-allocate the matrix for alignment, dimension + 1 for initializations */
        this.s = new int[xLength + 1][yLength + 1];

        /* Initialize the first row */
        for (int ii = 1; ii < xLength + 1; ii++) {
            this.s[ii][0] = getScore(1, 0) * ii;
        }

        /* Initialize the first column */
        for (int jj = 1; jj < yLength + 1; jj++) {
            this.s[0][jj] = getScore(0, 1) * jj;
        }
        /* Initialize top left corner */
        this.s[0][0] = 0;

    }

    /**
     * This is a sequential implementation of the Smith-Waterman alignment algorithm used for
     *  sub-matrices in the parallel implementation of scoreSequences.
     */
    private int scoreBabyMatrix(final String x, final String y, int rowstart, int colstart, int rowend, int colend) {
        /* Iterate over the baby matrix */
        for (int row = rowstart; row <= rowend; row++) {
            for (int col = colstart; col <= colend; col++) {
                /* The two characters to be compared */
                final char XChar = x.charAt(row - 1);
                final char YChar = y.charAt(col - 1);

                /* Find the largest point to jump from, and use it */
                final int diagScore = this.s[row - 1][col - 1] + getScore(charMap(XChar), charMap(YChar));
                final int topColScore = this.s[row - 1][col] + getScore(charMap(XChar), 0);
                final int leftRowScore = this.s[row][col - 1] + getScore(0, charMap(YChar));
                int score = Math.max(diagScore, Math.max(leftRowScore, topColScore));
                doWork(1);
                this.s[row][col] = score;
            }
        }

        /* Return the score of this baby matrix (for testing) */
        return this.s[rowend - 1][colend - 1];
    }


    /**
     * This is a parallel implementation of the Smith-Waterman alignment algorithm that
     * achieves the smallest execution time by using a matrix of Data Driven Futures and chunking.
     */
    public int scoreSequences(final String x, final String y) throws SuspendableException {
        if (this.xLength <= 0 || this.yLength <= 0) {
            throw new IllegalArgumentException("Lengths (" + this.xLength + ", " + this.yLength + ") must be positive!");
        }

        /*
         * STEP 1 : INITIALIZATION 🏁
         */
        final HjDataDrivenFuture<Integer>[][] ddfMatrix;
        final int xPartition;
        final int yPartition;

        /* STEP 1.1 : PARTITIONING */

        xPartition = (int)Math.ceil((double) this.xLength / 80);
        yPartition = (int)Math.ceil((double) this.yLength / 80);

        int numberXPartitions = this.xLength / xPartition;
        int numberYPartitions = this.yLength / yPartition;

        /* STEP 1.2 : CREATE DDF MATRIX */

        ddfMatrix = new HjDataDrivenFuture[numberXPartitions + 1][numberYPartitions + 1];

        /* Initialize DDF Matrix */
        for (int i = 0; i <= numberXPartitions; i++) {
            for (int j = 0; j <= numberYPartitions; j++) {
                ddfMatrix[i][j] = newDataDrivenFuture();
            }
        }

        /* Initialize top corner */
        ddfMatrix[0][0].put(0);

        /* Initialize first row */
        for (int row = 1; row <= numberXPartitions; row++) {
            ddfMatrix[row][0].put(0);
        }

        /* Initialize first column */
        for (int col = 1; col <= numberYPartitions; col++) {
            ddfMatrix[0][col].put(0);
        }

        /*
         * STEP 2: SCORING 🧮
         */
        finish(() -> {
            /* Iterate over the DDF Matrix */
            for (int i = 1; i <= numberXPartitions; i++) {
                for (int j = 1; j <= numberYPartitions; j++) {
                    final int ii = i;
                    final int jj = j;

                    /* Get this DDF's dependencies */
                    // ⬆️
                    HjFuture<Integer> top = ddfMatrix[ii - 1][jj];
                    // ↖️
                    HjFuture<Integer> diag = ddfMatrix[ii - 1][jj - 1];
                    // ⬅️
                    HjFuture<Integer> left = ddfMatrix[ii][jj - 1];

                    /* Wait for the dependencies */
                    asyncAwait(top, left, diag, () -> {

                        /* Convert DDF indices to Scoring Matrix indices */
                        int rowstart = ((ii - 1) * xPartition) + 1;
                        int colstart = ((jj - 1) * yPartition) + 1;
                        int rowend = Math.min(ii * xPartition, xLength);
                        int colend = Math.min(jj * yPartition, yLength);

                        /* Score this sub-matrix */
                        int chunkScore =  scoreBabyMatrix(x, y, rowstart, colstart, rowend, colend);

                        /* For testing */
                        ddfMatrix[ii][jj].put(chunkScore);
                    });
                }
            }
        });

        /* Return the score contained in the bottom right corner of the Scoring Matrix */
        return this.s[this.xLength][this.yLength];
    }


}



