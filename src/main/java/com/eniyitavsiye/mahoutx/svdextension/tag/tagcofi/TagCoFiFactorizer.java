/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.svdextension.tag.tagcofi;

import com.eniyitavsiye.mahoutx.common.UserItemIDIndexMapFunction;
import java.util.Iterator;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.recommender.svd.AbstractFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.DenseMatrix;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.DiagonalMatrix;
import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.MatrixSlice;
import org.apache.mahout.math.SparseMatrix;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.Vector.Element;
import org.apache.mahout.math.function.DoubleDoubleFunction;
import org.apache.mahout.math.function.DoubleFunction;
import org.apache.mahout.math.function.PlusMult;
import org.apache.mahout.math.function.VectorFunction;

/**
 *
 * @author ceyhun
 */
public class TagCoFiFactorizer extends AbstractFactorizer implements UserItemIDIndexMapFunction {
	
	private static final Logger log = Logger.getLogger(TagCoFiFactorizer.class.getName());

	///home/ceyhun/Dropbox/Projects/doctoral/dataset/MovieLens/10M100K
	/**
	 * Dispersion parameter for gaussian in euclidean-based similarity.
	 */
	private static final double EUCLIDEAN_SIGMA = 0.5;

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

	private final int N;
	private final int M;
	private int T;

	public TagCoFiFactorizer(DataModel dataModel,  
					SimilarityCalculator similarityCalculator, int D, int W, 
					double delta, double alpha, double beta) throws TasteException {
		super(dataModel);
		this.dataModel = dataModel;
		this.similarityCalculator = similarityCalculator;
		this.D = D;
		this.W = W;
		this.delta = delta;
		this.alpha = alpha;
		this.beta = beta;
		this.N = dataModel.getNumUsers();
		this.M = dataModel.getNumItems();
	}

	public void setUserTagMatrix(Matrix userTagMatrix) {
		this.userTagMatrix = userTagMatrix;
		this.T = userTagMatrix.rowSize();
	}

	@Override
	public Factorization factorize() throws TasteException {
		if (userTagMatrix == null) {
			RuntimeException e = new IllegalStateException("userTagMatrix is null! Before trying to build the model, set it first.");
			log.log(Level.SEVERE, e.getMessage(), e);
			throw e;
		}
		log.log(Level.INFO, "Factorization begins... delta:{0}, alpha:{1}, beta:{2}, W:{3}, D:{4}", 
						new Object[] {delta, alpha, beta, W, D});
		Matrix R = extractRatingsKillDataModel();
		Matrix Z = computeTF_IDF(); //from tags
		Matrix S = similarityCalculator.calculateSimilarityFrom(Z, userTagMatrix);
		log.log(Level.INFO, "Similarity calculation completed. {0}", similarityCalculator);

		//D = diagonal matrix with column sums of S.
		//L = D - S (Laplacian)
		Matrix L = new DiagonalMatrix(S.aggregateColumns(new VectorFunction() {
			@Override
			public double apply(Vector f) {
				return f.zSum();
			}
		})).minus(S);

		log.log(Level.INFO, "Laplacian calculated.");
		Matrix U = initRandom(D, N, 0.1);
		Matrix V = initRandom(D, M, 0.1);

		DiagonalMatrix alphaI = new DiagonalMatrix(alpha, M);
		Matrix betaL = L.times(beta);
		log.log(Level.INFO, "U and V matrices initialized, alphaI and betaL precalculated.");

		//for each iteration
		for (int w = 1; w <= W; ++w) {

			//for each factor
			for (int d = 1; d <= D; ++d) {
				Matrix W_mat = new DiagonalMatrix(computeVdjSquareSums(R, V, d));
				Vector x = computeXVector(R, U, V, d);
				Vector Ud = U.viewRow(d);
				Vector grad_f_Ud = W_mat.plus(alphaI).plus(betaL).times(Ud).minus(x);
				Ud.assign(Ud.minus(grad_f_Ud.times(delta)));
			}
			//for each item
			for (int j = 1; j <= M; ++j) {
				Vector Vj = V.viewRow(j);
				Vector grad_f_Vj = (alphaI.plus(sumOuterUserFactorProducts(R, U, j))).times(Vj).minus(sumUserFactorWithJthItemRatings(R, U, j));
				Vj.assign(Vj.minus(grad_f_Vj.times(delta)));
			}
			log.log(Level.INFO, "Iteration {0} completed.", w);
		
		}
		log.log(Level.INFO, "Factorization completed successfully.");
		return createFactorization(extractDoubleArray(U), extractDoubleArray(V));
	}
	
	//TODO check if every dense implementation usage is strictly necessary!

	private double[][] extractDoubleArray(Matrix m) {
		double[][] arr = new double[m.numRows()][m.numCols()];
		for (int i = 0; i < arr.length; ++i) {
			for (int j = 0; j < arr[i].length; ++j) {
				arr[i][j] = m.get(i, j);
			}
		}
		return arr;
	}

	private double[] computeVdjSquareSums(Matrix R, Matrix V, int d) {
		double[] vec = new double[N];
		for (int i = 0; i < N; ++i) {
			Iterator<Element> nonZeroRatingsOfI = R.viewRow(i).iterateNonZero();
			while (nonZeroRatingsOfI.hasNext()) {
				int j = nonZeroRatingsOfI.next().index();
				double vdj = V.get(d, j);
				vec[i] += vdj*vdj;
			}
		}
		return vec;
	}

	private Matrix extractRatingsKillDataModel() throws TasteException {
		Matrix r = new SparseMatrix(N, M);
		LongPrimitiveIterator it = dataModel.getUserIDs();
		while (it.hasNext()) {
			long userID = it.nextLong();
			int u = userIndex(userID);
			PreferenceArray userPrefs = dataModel.getPreferencesFromUser(userID);
			for (Preference pref : userPrefs) {
				int i = itemIndex(pref.getItemID());
				r.set(u, i, pref.getValue());
			}
		}
		//from now on, this class does not need dataModel anymore.
		dataModel = null;
		log.log(Level.INFO, "Rating data extracted.");
		return r;
	}

	private Matrix computeTF_IDF() {
		//TODO Search lucene for TF IDF implementation
		//document: Tags
		//terms   : user
		//tf(i,k) = userTagMatrix(i, k) / max(userTagMatrix(*, k))
		//idf(i) = log(T/sum(userTagMatrix(i, *) != 0))
		Vector idfs = userTagMatrix
						.aggregateRows(new CountNonZeroFunction())
						.assign(new DivideConstantInverseLogFunction(T));
		Vector colMaxes = userTagMatrix.aggregateColumns(new MaxFunction());
		Matrix z = new SparseMatrix(N, T);

		Iterator<MatrixSlice> matrixIterator = userTagMatrix.iterator();
		while (matrixIterator.hasNext()) {
			MatrixSlice slice = matrixIterator.next();

			int i = slice.index();

			Iterator<Element> rowIterator = slice.vector().iterateNonZero();
			while (rowIterator.hasNext()) {
				Element elem = rowIterator.next();

				int j = elem.index();

				double tf = elem.get() / colMaxes.get(j);
				double idf = idfs.get(j);
				double tf_idf = tf * Math.log(N/idf)/Math.log(2);

				if (isValidValue(tf_idf)) { //prevent NaN;
					z.set(i, j, tf_idf);
				}

			}
		}
		log.log(Level.INFO, "TF-IDF computation successful.");
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

	private Vector computeXVector(Matrix R, Matrix U, Matrix V, int d) {
		Vector vec = new DenseVector(N);
		for (int i = 0; i < N; ++i) {
			double sum = 0;
			Iterator<Element> nonZeroRatingsOfI = R.viewRow(i).iterateNonZero();
			while (nonZeroRatingsOfI.hasNext()) {
				Element elem = nonZeroRatingsOfI.next();
				int j = elem.index();
				double rij = elem.get();
				double vdj = V.get(d, j);
				double uI_dot_vJ = U.viewColumn(i).dot(V.viewColumn(j));
				double udivdj = vdj * U.get(d, i);
				sum += vdj * (rij - uI_dot_vJ + udivdj);
			}
			vec.set(i, sum);
		}
		return vec;
	}

	private Matrix sumOuterUserFactorProducts(Matrix R, Matrix U, int j) {
		Matrix result = new DenseMatrix(N, N);
		Iterator<Element> nonZeroRatingsOfI = R.viewColumn(j).iterateNonZero();
		
		while (nonZeroRatingsOfI.hasNext()) {
			Element elem = nonZeroRatingsOfI.next();
			int i = elem.index();
			Vector uI = U.viewColumn(i);
			result.assign(result.plus(uI.cross(uI)));
		}
		return result;
	}

	private Vector sumUserFactorWithJthItemRatings(Matrix R, Matrix U, int j) {
		Vector vec = new DenseVector(D);
		Iterator<Element> nonZeroRatingsOfI = R.viewColumn(j).iterateNonZero();
		while (nonZeroRatingsOfI.hasNext()) {
			Element elem = nonZeroRatingsOfI.next();
			int i = elem.index();
			double rij = elem.get();
			vec.assign(vec.plus(U.viewColumn(i).times(rij)));
		}
		return vec;
	}

	private static class NonZeroToOneFunction implements DoubleFunction {

		@Override
		public double apply(double arg1) {
			return arg1 != 0 ? 1 : 0;
		}

	}

	private static class DivideConstantInverseLogFunction implements DoubleFunction {

		private double logConstant;

		private DivideConstantInverseLogFunction(double constant) {
			logConstant = Math.log(constant);
		}

		@Override
		public double apply(double arg) {
			return logConstant - Math.log(arg);
		}

	}

	private class MaxFunction implements VectorFunction {

		@Override
		public double apply(Vector f) {
			return f.maxValue();
		}

	}

	private class CountNonZeroFunction implements VectorFunction {

		@Override
		public double apply(Vector f) {
			return f.aggregate(new PlusMult(1), new NonZeroToOneFunction());
		}

	}

	public enum SimilarityCalculator {
		
		COSINE {
			@Override
			public Matrix calculateSimilarityFrom(Matrix z, Matrix userTagMatrix) {
				int N = z.columnSize();
				Matrix s = new SparseMatrix(N, N);

				for (MatrixSlice slice : s) {
					int i = slice.index();
					Iterator<Element> rowIterator = slice.vector().iterateNonZero();

					while(rowIterator.hasNext()) {
						int j = rowIterator.next().index();

						Iterator<Element> commonTags = this.commonTagIterator(userTagMatrix, i, j);
						double dot = 0;
						double norm1 = 0;
						double norm2 = 0;
						while (commonTags.hasNext()) {
							int k = commonTags.next().index();
							double zik = z.get(i, k);
							double zij = z.get(i, j);
							dot += zik * zij;
							norm1 += zik * zik;
							norm2 += zij * zij;
						}
						double sim = dot / Math.sqrt(norm1 * norm2);
						if (isValidValue(sim)) {
							s.set(i, j, sim);
						}
					}
				}
				return s;
			}

		},
		PEARSON {
			@Override
			public Matrix calculateSimilarityFrom(Matrix z, Matrix userTagMatrix) {
				int N = z.columnSize();
				Matrix s = new SparseMatrix(N, N);
				Vector zColMeans = z.aggregateColumns(new VectorFunction() {
					@Override
					public double apply(Vector f) {
						return f.zSum() / f.size();
					}
				});

				for (MatrixSlice slice : s) {
					int i = slice.index();
					Iterator<Element> rowIterator = slice.vector().iterateNonZero();

					while(rowIterator.hasNext()) {
						int j = rowIterator.next().index();

						Iterator<Element> commonTags = this.commonTagIterator(userTagMatrix, i, j);
						double dot = 0;
						double norm1 = 0;
						double norm2 = 0;
						while (commonTags.hasNext()) {
							int k = commonTags.next().index();
							double zik = z.get(i, k) - zColMeans.get(i);
							double zij = z.get(i, j) - zColMeans.get(j);
							dot += zik * zij;
							norm1 += zik * zik;
							norm2 += zij * zij;
						}
						double sim = dot / Math.sqrt(norm1 * norm2);
						sim = 1 / (1 + Math.exp(sim));
						
						if (isValidValue(sim)) {
							s.set(i, j, sim);
						}
					}
				}
				return s;
			}
		},
		EUCLIDEAN {
			@Override
			public Matrix calculateSimilarityFrom(Matrix z, Matrix userTagMatrix) {
				int N = z.columnSize();
				Matrix m = new SparseMatrix(N, N);
				for (MatrixSlice slice : m) {
					int i = slice.index();
					Iterator<Element> rowIterator = slice.vector().iterateNonZero();
					while (rowIterator.hasNext()) {
						Element elem = rowIterator.next();
						int j = elem.index();
						Iterator<Element> commonTags = commonTagIterator(userTagMatrix, i, j);
						double sim = 0;
						while (commonTags.hasNext()) {
							int k = commonTags.next().index();
							double zik = z.get(i, k);
							double zjk = z.get(j, k);
							double d = zik - zjk;
							sim += d * d;
						}
						sim = Math.exp(-sim / (2 * EUCLIDEAN_SIGMA * EUCLIDEAN_SIGMA));
						if (isValidValue(sim)) {
							m.set(i, j, sim);
						}
					}


				}
				return m;
			}
		};

		
		abstract Matrix calculateSimilarityFrom(Matrix Z, Matrix userTagMatrix);

		protected Iterator<Element> commonTagIterator(Matrix tag, int i, int j) {
			Vector tagsOfUserI = tag.viewRow(i);
			Vector tagsOfUserJ = tag.viewRow(j);

			Vector ijmtimes = tagsOfUserI.clone().assign(tagsOfUserJ, new DoubleDoubleFunction() {
				@Override
				public double apply(double arg1, double arg2) {
					return arg1 * arg2;
				}

			});
			Iterator<Element> commonTags = ijmtimes.iterateNonZero();
			return commonTags;
		}
	}
	
	private static boolean isValidValue(double sim) {
		return sim != 0 && !Double.isNaN(sim) && !Double.isInfinite(sim);
	}

	@Override
	public Integer userIndex(long id) {
		return super.userIndex(id);
	}

	@Override
	public Integer itemIndex(long id) {
		return super.itemIndex(id);
	}

	@Override
	public int getUserCount() {
		return N;
	}

	@Override
	public int getItemCount() {
		return M;
	}

}
