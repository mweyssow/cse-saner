package cse;

import slp.core.modeling.runners.ModelRunner;
import slp.core.lexing.runners.LexerRunner;
import slp.core.modeling.Model;
import slp.core.translating.Vocabulary;
import slp.core.util.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.DoubleSummaryStatistics;

final class CompletionModelRunner extends ModelRunner {

    static int COMPLETION_CUTOFF = 10;

    CompletionModelRunner(Model model, LexerRunner lexerRunner, Vocabulary vocabulary) {
        super(model, lexerRunner, vocabulary);
    }

    void setTmpCompletionCutOff(int value) {
        setPredictionCutoff(value);
    }

    void setCompletionCutoff(int value) {
        COMPLETION_CUTOFF = value;
    }

    void completeLastTokenLine(String content) {
        completeLastToken(this.lexerRunner.lexLine(content));
    }

    List<Completion> completeLastTokenLines(List<String> content, List<String> suggestions) {
        List<Completion> rankingTmp = content.stream()
                .map(p -> completeLastToken(this.lexerRunner.lexLine(p)))
                .collect(Collectors.toList());

        // add static analysis suggestions corresponding to each completion
        List<Completion> rankings = IntStream.range(0, rankingTmp.size())
                .mapToObj(i -> {
                    Completion ranking = rankingTmp.get(i);
                    ranking.setSuggestions(
                            this.vocabulary.toIndices(
                                Arrays.asList(suggestions.get(i).split(" "))
                            )
                    );
                    return ranking;
                }).collect(Collectors.toList());
        rankings.forEach(p -> p.filterSuggestions(COMPLETION_CUTOFF));

        return rankings;
    }

    private Completion completeLastToken(Stream<String> lexed) {
        List<Integer> tokens = this.vocabulary.toIndices(lexed).collect(Collectors.toList());
        // Context is made of the previous tokens without the EOS special token (so -2)
        List<Integer> ctxTokens = tokens.stream().limit(tokens.size() - 2).collect(Collectors.toList());

        List<Map<Integer, Pair<Double, Double>>> preds = this.model.predict(ctxTokens);
        Map<Integer, Pair<Double, Double>> lastPred = preds.get(preds.size() - 2);
        List<Pair<Integer, Double>> completions = lastPred.entrySet().stream()
                .map(e -> Pair.of(e.getKey(), toProb(e.getValue())))
                .sorted((p1, p2) -> - Double.compare(p1.right, p2.right))
                .collect(Collectors.toList());

        return new Completion(tokens.get(tokens.size() - 2), completions);
    }

    DoubleSummaryStatistics getCompletionMRR(List<Completion> completions) {
        List<Double> MRRs = completions.stream()
                .map(l -> toMRR(l.getRank()))
                .collect(Collectors.toList());
        return getFileStats(Stream.of(MRRs));
    }

    public DoubleSummaryStatistics getCompletionRecall(List<Completion> completions) {
        List<Double> recalls = completions.stream()
                .map(Completion::getRecall)
                .collect(Collectors.toList());
        return getFileStats(Stream.of(recalls));
    }

    private DoubleSummaryStatistics getFileStats(Stream<List<Double>> fileProbs) {
        return fileProbs.flatMap(List::stream)
                .mapToDouble(p -> p).summaryStatistics();
    }
}
