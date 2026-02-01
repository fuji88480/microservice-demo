import { Link } from 'react-router-dom'
import { Button } from '../ui/button'

export default function Header() {
  return (
    <div className="bg-white border-b">
      <div className="h-14 flex items-center justify-between px-6 sm:px-8">
        <div>
          <Link to="/" className="flex items-center gap-2">
            <img src="/logo.jpeg" alt="logo" className="h-8" />
            <span className="font-mono font-semibold">GitTix</span>
          </Link>
        </div>

        <div className="flex gap-4 font-mono">
          <Link to="/auth/signin">
            <Button size="sm" variant="outline">ログイン／新規登録</Button>
          </Link>

          {/* <div>
            <Link
              to="/auth/signup"
              className="flex items-center gap-2"
            >
              <span className="">ORDERS</span>
            </Link>
          </div>
          <div>
            <Link
              to="/auth/signup"
              className="flex items-center gap-2"
            >
              <span className="">LOGOUT</span>
            </Link>
          </div> */}
        </div>
      </div>
    </div>
  )
}
