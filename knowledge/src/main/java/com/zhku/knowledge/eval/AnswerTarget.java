package com.zhku.knowledge.eval;

/**
 * 评测的被测系统端口：给一个问题，返回实际观测结果。
 *
 * <p>把"被测系统"抽成端口，执行器就能对**真实链路**（本机预览门面）与**离线用例替身**跑同一套
 * 对比逻辑——离线替身让执行器自身的逻辑先被测死，接真链路只是换一个实现。</p>
 */
@FunctionalInterface
public interface AnswerTarget {

    ObservedAnswer answer(String question);
}
