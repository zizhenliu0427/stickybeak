/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // 桉树绿（品牌主色）
        brand: {
          50: '#f0f7f3',
          100: '#dcebe2',
          200: '#b9d6c7',
          300: '#8fbba6',
          400: '#5f9c81',
          500: '#3d8066',
          600: '#2c6752',
          700: '#255443',
          800: '#204438',
          900: '#1b382f',
        },
        // 落日陶土橙（点缀 CTA）
        accent: {
          50: '#fdf3ec',
          100: '#fae3d3',
          200: '#f5c4a3',
          300: '#efa06f',
          400: '#e97f42',
          500: '#e0651f',
          600: '#c55418',
          700: '#a34316',
          800: '#853718',
          900: '#6d2f16',
        },
        // 暖沙底色
        sand: {
          50: '#faf8f4',
          100: '#f4f0e8',
          200: '#e6dfd2',
        },
      },
      fontFamily: {
        display: ['Georgia', 'Times New Roman', 'serif'],
      },
    },
  },
  plugins: [],
};
