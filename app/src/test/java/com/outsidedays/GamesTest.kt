package com.outsidedays

import com.outsidedays.core.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class GamesTest {
    @Test fun slidingPuzzlesAreSolvableAndNotAlreadySolved() {
        repeat(100) { seed -> val g = Games.new("Cloud Slide", random = Random(seed)); assertTrue(Games.solvable(g.board)); assertNotEquals((1..8).toList()+0,g.board) }
        assertFalse(Games.solvable(listOf(2,1,3,4,5,6,7,8,0)))
    }
    @Test fun slideOnlyAllowsNeighborsAndRecognizesCompletion() {
        val g = Game("Cloud Slide",size=3,board=listOf(1,2,3,4,5,6,7,0,8))
        assertEquals(g, Games.slide(g,0)); assertEquals("won",Games.slide(g,8).status)
    }
    @Test fun bothSudokuPuzzlesHaveExactlyOneValidSolution() {
        listOf(4,9).forEach { n -> val g = Games.new("Pocket Sudoku",n); assertEquals(1,Sudoku.solutions(g.givens,n).size); assertTrue(Sudoku.conflicts(g.solution,n).isEmpty()); Backups.validateGame(g) }
    }
    @Test fun sudokuPreservesCluesAndSupportsNotesConflictsHintsAndWin() {
        var g = Games.new("Pocket Sudoku",4)
        assertEquals(g,Games.sudoku(g,0,3,false))
        val i = g.givens.indexOf(0)
        g = Games.sudoku(g,i,1,true); assertEquals(1 shl 1,g.notes[i]); assertEquals(0,g.board[i])
        g = Games.sudoku(g,i,1,false); assertTrue(Sudoku.conflicts(g.board,4).isNotEmpty())
        g = Games.hint(g,i); assertEquals(g.solution[i],g.board[i]); assertEquals(1,g.hints)
        repeat(16) { g = Games.hint(g,-1) }; assertEquals("won",g.status)
    }
    @Test fun mergeNeverMergesNewTileTwiceInOneMove() {
        assertEquals(listOf(4,4,0,0) to 8, Games.collapse(listOf(2,2,2,2)))
        assertEquals(listOf(4,4,0,0) to 4, Games.collapse(listOf(2,2,4,0)))
        assertEquals(listOf(8,0,0,0) to 8, Games.collapse(listOf(4,0,4,0)))
    }
    @Test fun mergeMovesSpawnOnlyOnChangedBoardsAndDetectGameOver() {
        val g = Game("Cozy Merge",board=listOf(2,0,0,0)+List(12){0})
        assertEquals(g,Games.merge(g,0,Random(1)))
        val moved = Games.merge(g,1,Random(1)); assertEquals(2,moved.board.count{it!=0}); assertEquals(1,moved.moves)
        assertFalse(Games.canMove(listOf(2,4,2,4,4,2,4,2,2,4,2,4,4,2,4,2)))
        val won = Games.merge(Game("Cozy Merge",board=listOf(1024,1024,0,0)+List(12){0}),0,Random(2)); assertEquals("won",won.status)
    }
    @Test fun leafMatchFinishesAndCannotSelectSameCardTwice() {
        var g = Games.new("Leaf Match",3,random=Random(4))
        val once = Games.leaf(g,0); assertEquals(once,Games.leaf(once,0))
        g.board.toSet().forEach { value -> g.board.indices.filter { g.board[it]==value }.forEach { g=Games.leaf(g,it) } }
        assertEquals("won",g.status); assertEquals(12,g.matched.size)
    }
    @Test fun fireflyRoundHasRealCompletionAndSavedBestScore() {
        var g = Games.new("Firefly Garden",random=Random(1)); g=Games.firefly(g,g.target,Random(2)); assertEquals(1,g.score)
        g=Games.tick(g,30_000); assertEquals("won",g.status); assertEquals(1,g.best); assertEquals(g,Games.firefly(g,g.target))
    }
}
