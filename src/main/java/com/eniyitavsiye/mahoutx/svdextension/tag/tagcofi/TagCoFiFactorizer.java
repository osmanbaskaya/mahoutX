/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.svdextension.tag.tagcofi;

import java.util.Random;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender;
import org.apache.mahout.cf.taste.impl.recommender.svd.AbstractFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.DenseMatrix;
import org.apache.mahout.math.DiagonalMatrix;
import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.SparseMatrix;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.function.DoubleDoubleFunction;
import org.apache.mahout.math.function.VectorFunction;

/**
 *
 * @author ceyhun
 */
public class TagCoFiFactorizer extends AbstractFactorizer {

	/**
	 * Number of factors;
	 */
	private int D;
	/**
	 * Number of iterations;
	 */
	private int W;
	/**
	 * Gradient descent step size.
	 */
	private double delta;
	/**
	 * Tag matrix of users.
	 */
	private Matrix userTagMatrix;
	/**
	 * Similarity calculator.
	 */
	private SimilarityCalculator similarityCalculator;

	//hyperparameters?
	private double alpha;
	private double beta;

	private DataModel dataModel;

	public TagCoFiFactorizer(DataModel dataModel, Matrix userTagMatrix, 
					SimilarityCalculator similarityCalculator, int D, int W, 
					double delta, double alpha, double beta) throws TasteException {
		super(dataModel);
		this.dataModel = dataModel;
		this.userTagMatrix = userTagMatrix;
		this.similarityCalculator = similarityCalculator;
		this.D = D;
		this.W = W;
		this.delta = delta;
		this.alpha = alpha;
		this.beta = beta;
	}

	@Override
	public Factorization factorize() throws TasteException {
		Matrix R = extractRatings();
		Matrix Z = computeTF_IDF(); //from tags
		Matrix S = similarityCalculator.calculateSimilarityFrom(Z, userTagMatrix);

		//D = diagonal matrix with column sums of S.
		//L = D - S (Laplacian)
		Matrix L = new DiagonalMatrix(S.aggregateColumns(new VectorFunction() {
			@Override
			public double apply(Vector f) {
				return f.zSum();
			}
		})).minus(S);

		final int M = R.rowSize(); //number of items
		final int N = R.columnSize(); // number of user.

		Matrix U = initRandom(D, N, 0.1);
		Matrix V = initRandom(D, M, 0.1);

		DiagonalMatrix alphaI = new DiagonalMatrix(alpha, M);
		Matrix betaL = L.times(beta);

		//for each iteration
		for (int w = 1; w <= W; ++w) {

			//for each factor
			for (int d = 1; d <= D; ++d) {
				Matrix W = new DiagonalMatrix(computeVdjSquareSums(R, V));
				Vector x = computeXVector(R, U, V);
				Vector Ud = U.viewRow(d);
				Vector grad_f_Ud = W.plus(alphaI).plus(betaL).times(Ud).minus(x);
				Ud.assign(Ud.minus(grad_f_Ud.times(delta)));
			}
			//for each item
			for (int j = 1; j <= M; ++j) {
				Vector Vj = V.viewRow(j);
				Vector grad_f_Vj = (alphaI.plus(sumOuterUserFactorProducts(R, U))).times(Vj).minus(sumUserFactorWithJthItemRatings(R, U));
				Vj.assign(Vj.minus(grad_f_Vj.times(delta)));
			}
		
		}
		return createFactorization(extractDoubleArray(U), extractDoubleArray(V));
	}
	
	private double[][] extractDoubleArray(Matrix m) {
		double[][] arr = new double[m.numRows()][m.numCols()];
		for (int i = 0; i < arr.length; ++i) {
			for (int j = 0; j < arr[i].length; ++j) {
				arr[i][j] = m.get(i, j);
			}
		}
		return arr;
	}

	private double[] computeVdjSquareSums(Matrix R, Matrix V) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private Matrix extractRatings() throws TasteException {
		int N = dataModel.getNumUsers();
		int M = dataModel.getNumItems();
		Matrix r = new SparseMatrix(N, M);
		LongPrimitiveIterator it = dataModel.getUserIDs();
		while (it.hasNext()) {
			long userID = it.nextLong();
			int u = userIndex(userID);
			PreferenceArray userPrefs = dataModel.getPreferencesFromUser(userID);
			for (long itemID : userPrefs.getIDs()) {
				int i = itemIndex(itemID);
				r.set(u, i, userPrefs.getValue(i));
			}
		}
		return r;
	}

	private Matrix computeTF_IDF() {
		int N = userTagMatrix.columnSize();
		int T = userTagMatrix.rowSize();
		//TODO SparseMatrix could be used instead.
		Matrix z = new DenseMatrix(N, T);
		for (int i = 0; i < N; ++i) {
			for (int j = 0; j < T; ++j) {
				double tf = userTagMatrix.get(i, j); //TODO calculate normalized frequency!
				double idf = userTagMatrix.viewColumn(j).zSum(); //TODO this may mean counting non-zeros.
				if (idf == 0) { //prevent NaN
					z.set(i, j, 0);
				} else {
					double tf_idf = tf * Math.log(N/idf)/Math.log(2);
					z.set(i, j, tf_idf);
				}
			}
		}
		return z;
	}

	private Matrix initRandom(int D, int N, double mag) {
		Random random = RandomUtils.getRandom();
		Matrix m = new DenseMatrix(D, N);
		for (int i = 0; i < D; ++i) {
			for (int j = 1; j < N; j++) {
				m.set(i, j, random.nextDouble() * mag - mag/2);
			}
		}
		return m;
	}

	private Vector computeXVector(Matrix R, Matrix U, Matrix V) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private DiagonalMatrix sumOuterUserFactorProducts(Matrix R, Matrix U) {
		int N = U.rowSize();
		double[] values = new double[N];
		for (int i = 0; i < N; ++i) {

		}
		return new DiagonalMatrix(values);
	}

	private Vector sumUserFactorWithJthItemRatings(Matrix R, Matrix U) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	public enum SimilarityCalculator {
		
		COSINE {
			@Override
			public Matrix calculateSimilarityFrom(Matrix z, Matrix userTagMatrix) {
				int N = z.columnSize();
				Matrix s = new SparseMatrix(N, N);

				for (int i = 0; i < N; ++i) {

					Vector tagsOfUserI = userTagMatrix.viewRow(i);
					for (int j = i + 1; j < N; ++j) {

						Vector tagsOfUserJ = userTagMatrix.viewRow(j);
						Vector ijmtimes = tagsOfUserI.clone().assign(tagsOfUserJ, new DoubleDoubleFunction() {

							@Override
							public double apply(double arg1, double arg2) {
								return arg1 * arg2;
							}

						});
						ijmtimes.iterateNonZero();

					}
				}
				return s;
			}
		},
		PEARSON {
			@Override
			public Matrix calculateSimilarityFrom(Matrix z, Matrix userTagMatrix) {
				throw new UnsupportedOperationException("Not yet implemented");
			}
		},
		EUCLIDEAN {
			@Override
			public Matrix calculateSimilarityFrom(Matrix z, Matrix userTagMatrix) {
				throw new UnsupportedOperationException("Not yet implemented");
			}
		};
		
		abstract Matrix calculateSimilarityFrom(Matrix Z, Matrix userTagMatrix);

	}
	
}
